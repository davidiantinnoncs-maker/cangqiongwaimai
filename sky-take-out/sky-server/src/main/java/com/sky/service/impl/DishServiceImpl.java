package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DishServiceImpl implements DishService {

    private static final String DISH_CACHE_KEY_PREFIX = "dish:list:";
    private static final long DISH_CACHE_TTL_SECONDS = 1800;
    private static final long DISH_CACHE_TTL_JITTER_SECONDS = 600;
    private static final long DISH_EMPTY_CACHE_TTL_SECONDS = 60;

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增菜品（含口味）
     *
     * @param dishDTO
     */
    @Override
    @Transactional
    public void saveWithFlavors(DishDTO dishDTO) {
        // 1. 将DTO转为菜品实体并插入
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);

        // 2. 获取插入后生成的菜品id
        Long dishId = dish.getId();

        // 3. 处理口味数据：为每个口味设置菜品id
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dishId));
            // 4. 批量插入口味
            dishFlavorMapper.insertBatch(flavors);
        }
        clearDishListCacheAfterCommit();
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * 规则：
     * 1. 起售中的菜品不能删除
     * 2. 被套餐关联的菜品不能删除
     * 3. 删除菜品后关联口味一并删除
     *
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 1. 查询菜品，检查起售状态
        List<Dish> dishes = dishMapper.getByIds(ids);
        List<Long> onSaleIds = dishes.stream()
                .filter(d -> StatusConstant.ENABLE.equals(d.getStatus()))
                .map(Dish::getId)
                .collect(Collectors.toList());
        if (!onSaleIds.isEmpty()) {
            throw new BaseException(MessageConstant.DISH_ON_SALE);
        }

        // 2. 检查是否被套餐关联
        Integer count = setmealDishMapper.countByDishIds(ids);
        if (count != null && count > 0) {
            throw new BaseException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 3. 删除菜品关联的口味
        dishFlavorMapper.deleteByDishIds(ids);

        // 4. 删除菜品
        dishMapper.deleteByIds(ids);
        clearDishListCacheAfterCommit();
    }

    /**
     * 根据id查询菜品（含口味）
     *
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavors(Long id) {
        // 1. 查询菜品（含分类名称）
        DishVO dishVO = dishMapper.getById(id);

        // 2. 查询关联的口味
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);
        dishVO.setFlavors(flavors);

        return dishVO;
    }

    /**
     * 修改菜品（含口味）
     *
     * @param dishDTO
     */
    @Override
    @Transactional
    public void updateWithFlavors(DishDTO dishDTO) {
        // 1. 修改菜品基本信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        // 2. 删除原有口味
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 3. 重新插入口味
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dishDTO.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
        clearDishListCacheAfterCommit();
    }
    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        if (dish.getCategoryId() == null || dish.getStatus() == null) {
            return queryListWithFlavor(dish);
        }

        String key = DISH_CACHE_KEY_PREFIX + dish.getCategoryId() + ":" + dish.getStatus();
        String cachedJson = readCache(key);
        if (cachedJson != null) {
            try {
                return JSON.parseArray(cachedJson, DishVO.class);
            } catch (Exception e) {
                log.error("菜品列表缓存反序列化失败，key={}", key, e);
            }
        }

        List<DishVO> dishVOList = queryListWithFlavor(dish);
        writeCache(key, dishVOList);
        return dishVOList;
    }

    /**
     * 条件查询菜品和口味（数据库兜底查询）
     *
     * @param dish
     * @return
     */
    private List<DishVO> queryListWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);

            // 根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    /**
     * 读取菜品列表缓存，Redis异常时降级为数据库查询
     */
    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("读取菜品列表缓存失败，key={}", key, e);
            return null;
        }
    }

    /**
     * 写入菜品列表缓存，空列表短时间缓存防止穿透
     */
    private void writeCache(String key, List<DishVO> dishVOList) {
        try {
            long ttl = dishVOList.isEmpty()
                    ? DISH_EMPTY_CACHE_TTL_SECONDS
                    : DISH_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(DISH_CACHE_TTL_JITTER_SECONDS);
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(dishVOList), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("写入菜品列表缓存失败，key={}", key, e);
        }
    }

    /**
     * 事务提交后清除菜品列表缓存
     */
    private void clearDishListCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearDishListCache();
                }
            });
        } else {
            clearDishListCache();
        }
    }

    /**
     * 使用SCAN清除所有菜品列表缓存
     */
    private void clearDishListCache() {
        try {
            Set<String> keys = stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> matchedKeys = new HashSet<>();
                try (Cursor<byte[]> cursor = connection.scan(
                        ScanOptions.scanOptions().match(DISH_CACHE_KEY_PREFIX + "*").count(100).build())) {
                    while (cursor.hasNext()) {
                        matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return matchedKeys;
            });
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("已清除菜品列表缓存，共{}个key", keys.size());
            }
        } catch (Exception e) {
            log.error("清除菜品列表缓存失败", e);
        }
    }
}
