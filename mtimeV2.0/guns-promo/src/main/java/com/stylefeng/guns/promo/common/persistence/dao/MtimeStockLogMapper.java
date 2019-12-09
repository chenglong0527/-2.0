package com.stylefeng.guns.promo.common.persistence.dao;

import com.stylefeng.guns.promo.common.persistence.model.MtimeStockLog;
import com.baomidou.mybatisplus.mapper.BaseMapper;
import com.stylefeng.guns.promo.common.persistence.vo.StockLogStatus;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author pandax
 * @since 2019-12-04
 */
public interface MtimeStockLogMapper extends BaseMapper<MtimeStockLog> {

    Integer updateStatusById(@Param("id") String stockLogId, @Param("status") Integer status);
}
