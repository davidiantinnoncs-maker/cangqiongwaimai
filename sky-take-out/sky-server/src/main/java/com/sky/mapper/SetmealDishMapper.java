package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 批量插入套餐菜品关系
     *
     * @param setmealDishes
     */
    void insertBatch(List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id删除套餐菜品关系
     *
     * @param setmealId
     */
    void deleteBySetmealId(Long setmealId);

    /**
     * 根据套餐id集合批量删除套餐菜品关系
     *
     * @param setmealIds
     */
    void deleteBySetmealIds(List<Long> setmealIds);

    /**
     * 根据套餐id查询套餐菜品关系
     *
     * @param setmealId
     * @return
     */
    List<SetmealDish> getBySetmealId(Long setmealId);

    /**
     * 根据套餐id查询关联的菜品id集合
     *
     * @param setmealId
     * @return
     */
    List<Long> getDishIdsBySetmealId(Long setmealId);

    /**
     * 根据菜品id集合查询套餐-菜品关联数量
     * @param dishIds
     * @return
     */
    Integer countByDishIds(List<Long> dishIds);

}
