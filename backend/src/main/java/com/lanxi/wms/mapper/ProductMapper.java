package com.lanxi.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanxi.wms.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** 绕过 @TableLogic 查询（含已逻辑删除），用于导入时按编码查重 */
    @Select("SELECT * FROM base_product WHERE code = #{code} LIMIT 1")
    Product selectByCodeIncludeDeleted(@Param("code") String code);

    /** 绕过 @TableLogic 按名称查重 */
    @Select("SELECT * FROM base_product WHERE name = #{name} LIMIT 1")
    Product selectByNameIncludeDeleted(@Param("name") String name);

    /** 恢复逻辑删除的商品（deleted 置 0） */
    @Update("UPDATE base_product SET deleted = 0 WHERE id = #{id}")
    int restoreDeleted(@Param("id") Long id);
}
