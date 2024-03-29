package com.stylefeng.guns.promo.common.persistence.dao;

import com.stylefeng.guns.promo.common.persistence.model.MtimeFieldT;
import com.baomidou.mybatisplus.mapper.BaseMapper;

/**
 * <p>
 * 放映场次表 Mapper 接口
 * </p>
 *
 * @author pandax
 * @since 2019-11-29
 */
public interface MtimeFieldTMapper extends BaseMapper<MtimeFieldT> {

    String selectSeatAddressByFieldId(Integer filedId);
}
