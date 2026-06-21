package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id集合查询套餐-菜品关联数量
     * @param dishIds
     * @return
     */
    Integer countByDishIds(List<Long> dishIds);

}
