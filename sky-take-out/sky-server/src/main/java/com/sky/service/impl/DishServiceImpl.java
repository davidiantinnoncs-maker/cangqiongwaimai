package com.sky.service.impl;

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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

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
    }
}
