package com.jzo2o.foundations.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.foundations.dto.response.ServeItemResDTO;
import com.jzo2o.api.foundations.dto.response.ServeItemSimpleResDTO;
import com.jzo2o.api.foundations.dto.response.ServeTypeCategoryResDTO;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.mapper.ServeItemMapper;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.mapper.ServeTypeMapper;
import com.jzo2o.foundations.model.domain.Region;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.domain.ServeItem;
import com.jzo2o.foundations.model.domain.ServeType;
import com.jzo2o.foundations.model.dto.request.*;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IRegionService;
import com.jzo2o.foundations.service.IServeItemService;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.foundations.service.IServeSyncService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 区域服务表 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-03
 */
@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {


    @Resource
    private ServeMapper serveMapper;

    @Resource
    private ServeItemMapper serveItemMapper;

    @Resource
    private IRegionService regionService;

    @Override
    public PageResult<ServeResDTO> page(ServePageQueryReqDTO servePageQueryReqDTO) {
        PageResult<ServeResDTO> serveResDTOPageResult = PageHelperUtils.selectPage(servePageQueryReqDTO,
                () -> baseMapper.queryServeListByRegionId(servePageQueryReqDTO.getRegionId()));
        return serveResDTOPageResult;
    }

    @Override
    @Transactional
    public void batchAdd(List<ServeUpsertReqDTO> serveUpsertReqDTOS) {
        //遍历serveUpsertReqDTOS
        serveUpsertReqDTOS.forEach(serveUpsertReqDTO -> {
            //服务项目id
            Long serveItemId = serveUpsertReqDTO.getServeItemId();
            //区域id
            Long regionId = serveUpsertReqDTO.getRegionId();
            //根据serveItemId查询服务项目
            ServeItem serveItem = serveItemMapper.selectById(serveItemId);
            //如果服务项目未启动则抛出异常
            if (ObjectUtil.isNull(serveItem) || FoundationStatusEnum.ENABLE.getStatus()!=serveItem.getActiveStatus()) {
                throw new ForbiddenOperationException("服务未启用");
            }
            //唯一性校验，同一个区域不能添加相同的服务项目
            Long count = lambdaQuery().eq(Serve::getServeItemId, serveItemId)
                    .eq(Serve::getRegionId, regionId).count();
            //如果count大于0抛出异常
            if (count>0) {
                throw new ForbiddenOperationException("服务已存在");
            }

            //准备数据，向serve插入数据
            Serve serve = BeanUtils.toBean(serveUpsertReqDTO, Serve.class);
            //根据区域id查询区域
            Region region = regionService.getById(regionId);
            //citycode
            serve.setCityCode(region.getCityCode());
            //price
            serve.setPrice(serveItem.getReferencePrice());
            //添加数据
            serveMapper.insert(serve);

        });




    }

    @Override
    public void update(Long id, BigDecimal price) {
        boolean update = lambdaUpdate()
                .set(Serve::getPrice, price)
                .eq(Serve::getId, id)
                .update();
        if (!update) {
            throw new ForbiddenOperationException("更新失败");
        }
    }

    @Override
    @Transactional
    public void onSale(Long id) {
        //根据id查询 serve
        Serve serve = baseMapper.selectById(id);
        //状态是草稿或下架时方可上架
        if (!(FoundationStatusEnum.INIT.getStatus() == serve.getSaleStatus() || FoundationStatusEnum.DISABLE.getStatus() == serve.getSaleStatus())) {
            throw new ForbiddenOperationException("草稿或下架状态方可上架");
        }
        //服务项目id
        Long serveItemId = serve.getServeItemId();
        //查询服务项目
        ServeItem serveItem = serveItemMapper.selectById(serveItemId);
        //如果服务项目未启动则不能上架
        if (ObjectUtil.isNull(serveItem) || FoundationStatusEnum.ENABLE.getStatus()!=serveItem.getActiveStatus()) {
            throw new ForbiddenOperationException("服务未启用,不能上架");
        }

        //数据存储，将serve的状态更新为已上架
        boolean update = lambdaUpdate().set(Serve::getSaleStatus, FoundationStatusEnum.ENABLE.getStatus())
                .eq(Serve::getId, id).update();
        if (!update) {
            throw new ForbiddenOperationException("上架失败");
        }

    }
}
