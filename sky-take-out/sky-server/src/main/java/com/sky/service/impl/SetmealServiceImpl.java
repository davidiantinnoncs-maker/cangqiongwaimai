package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 套餐业务实现
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    private static final String SETMEAL_CACHE_KEY_PREFIX = "setmeal:list:";
    private static final long SETMEAL_CACHE_TTL_SECONDS = 1800;
    private static final long SETMEAL_CACHE_TTL_JITTER_SECONDS = 600;
    private static final long SETMEAL_EMPTY_CACHE_TTL_SECONDS = 60;

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增套餐（含关联菜品）
     *
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);

        // 为每个套餐菜品关系设置套餐id并批量插入
        Long setmealId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }
        clearSetmealListCacheAfterCommit();
    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     * 起售中的套餐不能删除，删除套餐时同步删除关联菜品
     *
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 1. 检查是否存在起售中的套餐
        List<Setmeal> setmeals = setmealMapper.getByIds(ids);
        List<Long> onSaleIds = setmeals.stream()
                .filter(setmeal -> StatusConstant.ENABLE.equals(setmeal.getStatus()))
                .map(Setmeal::getId)
                .collect(Collectors.toList());
        if (!onSaleIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }

        // 2. 删除套餐关联的菜品
        setmealDishMapper.deleteBySetmealIds(ids);

        // 3. 删除套餐
        setmealMapper.deleteByIds(ids);
        clearSetmealListCacheAfterCommit();
    }

    /**
     * 根据id查询套餐（含关联菜品）
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        SetmealVO setmealVO = setmealMapper.getById(id);
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐（含关联菜品）
     *
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void updateWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);

        // 删除原有套餐菜品关系后重新插入
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealDTO.getId()));
            setmealDishMapper.insertBatch(setmealDishes);
        }
        clearSetmealListCacheAfterCommit();
    }

    /**
     * 套餐起售/停售
     *
     * @param status
     * @param id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        // 起售时校验套餐内是否包含停售菜品
        if (StatusConstant.ENABLE.equals(status)) {
            List<Long> dishIds = setmealDishMapper.getDishIdsBySetmealId(id);
            if (dishIds != null && !dishIds.isEmpty()) {
                boolean hasStoppedDish = dishMapper.getByIds(dishIds).stream()
                        .anyMatch(dish -> StatusConstant.DISABLE.equals(dish.getStatus()));
                if (hasStoppedDish) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
        clearSetmealListCacheAfterCommit();
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        if (setmeal.getCategoryId() == null || setmeal.getStatus() == null) {
            return setmealMapper.list(setmeal);
        }

        String key = SETMEAL_CACHE_KEY_PREFIX + setmeal.getCategoryId() + ":" + setmeal.getStatus();
        String cachedJson = readCache(key);
        if (cachedJson != null) {
            try {
                return JSON.parseArray(cachedJson, Setmeal.class);
            } catch (Exception e) {
                log.error("套餐列表缓存反序列化失败，key={}", key, e);
            }
        }

        List<Setmeal> list = setmealMapper.list(setmeal);
        writeCache(key, list);
        return list;
    }

    /**
     * 读取套餐列表缓存，Redis异常时降级为数据库查询
     */
    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("读取套餐列表缓存失败，key={}", key, e);
            return null;
        }
    }

    /**
     * 写入套餐列表缓存，空列表短时间缓存防止穿透
     */
    private void writeCache(String key, List<Setmeal> setmealList) {
        try {
            long ttl = setmealList.isEmpty()
                    ? SETMEAL_EMPTY_CACHE_TTL_SECONDS
                    : SETMEAL_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(SETMEAL_CACHE_TTL_JITTER_SECONDS);
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(setmealList), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("写入套餐列表缓存失败，key={}", key, e);
        }
    }

    /**
     * 事务提交后清除套餐列表缓存
     */
    private void clearSetmealListCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAllSetmealListCache();
                }
            });
        } else {
            deleteAllSetmealListCache();
        }
    }

    /**
     * 使用SCAN清除所有套餐列表缓存
     */
    private void deleteAllSetmealListCache() {
        try {
            Set<String> keys = stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Set<String> matchedKeys = new HashSet<>();
                try (Cursor<byte[]> cursor = connection.scan(
                        ScanOptions.scanOptions().match(SETMEAL_CACHE_KEY_PREFIX + "*").count(100).build())) {
                    while (cursor.hasNext()) {
                        matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return matchedKeys;
            });
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("已清除套餐列表缓存，共{}个key", keys.size());
            }
        } catch (Exception e) {
            log.error("清除套餐列表缓存失败", e);
        }
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
