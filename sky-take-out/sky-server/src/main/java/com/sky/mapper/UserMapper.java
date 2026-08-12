package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    /**
     * 根据 openid 查询用户
     *
     * @param openid 微信用户唯一标识
     * @return 用户
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 根据id查询用户
     *
     * @param id 用户id
     * @return 用户
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 新增用户
     *
     * @param user 用户
     */
    @Insert("insert into user (openid, name, phone, sex, id_number, avatar, create_time) " +
            "values (#{openid}, #{name}, #{phone}, #{sex}, #{idNumber}, #{avatar}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    /**
     * 更新用户资料
     *
     * @param user 用户
     */
    @Update("update user set name = #{name}, avatar = #{avatar}, sex = #{sex} where id = #{id}")
    void update(User user);
}
