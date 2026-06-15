package com.sky.mapper;

import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee
     */
    @Insert("INSERT INTO employee (username, name, password, phone, sex, id_number, " +
            "status, create_time, update_time, create_user, update_user) " +
            "VALUES (#{username}, #{name}, #{password}, #{phone}, #{sex}, #{idNumber}, " +
            "#{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Employee employee);

    /**
     * 分页查询员工（支持按姓名模糊查询）
     * @param employeePageQueryDTO
     * @return
     */
    List<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 根据ID查询员工
     * @param id
     * @return
     */
    @Select("select * from employee where id = #{id}")
    Employee getById(Long id);

    /**
     * 启用/禁用员工账号
     * @param id
     * @param status
     */
    @Update("update employee set status = #{status} where id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新员工信息
     * @param employee
     */
    void update(Employee employee);

}
