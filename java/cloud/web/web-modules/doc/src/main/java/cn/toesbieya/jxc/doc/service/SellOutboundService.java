package cn.toesbieya.jxc.doc.service;

import cn.toesbieya.jxc.api.RecordApi;
import cn.toesbieya.jxc.api.StockApi;
import cn.toesbieya.jxc.common.exception.JsonResultException;
import cn.toesbieya.jxc.common.model.entity.*;
import cn.toesbieya.jxc.common.model.vo.R;
import cn.toesbieya.jxc.common.model.vo.StockOutboundVo;
import cn.toesbieya.jxc.common.model.vo.UserVo;
import cn.toesbieya.jxc.common.util.Util;
import cn.toesbieya.jxc.doc.enumeration.DocFinishEnum;
import cn.toesbieya.jxc.doc.enumeration.DocHistoryEnum;
import cn.toesbieya.jxc.doc.enumeration.DocStatusEnum;
import cn.toesbieya.jxc.doc.mapper.*;
import cn.toesbieya.jxc.doc.model.vo.DocStatusUpdate;
import cn.toesbieya.jxc.doc.model.vo.SellOutboundExport;
import cn.toesbieya.jxc.doc.model.vo.SellOutboundSearch;
import cn.toesbieya.jxc.doc.model.vo.SellOutboundVo;
import cn.toesbieya.jxc.doc.util.DocUtil;
import cn.toesbieya.jxc.web.common.annoation.Lock;
import cn.toesbieya.jxc.web.common.annoation.UserAction;
import cn.toesbieya.jxc.web.common.model.vo.PageResult;
import cn.toesbieya.jxc.web.common.util.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SellOutboundService {
    @Resource
    private SellOutboundMapper mainMapper;
    @Resource
    private SellOutboundSubMapper subMapper;
    @Resource
    private SellOrderMapper orderMapper;
    @Resource
    private SellOrderSubMapper orderSubMapper;
    @Resource
    private DocHistoryMapper historyMapper;
    @Resource
    private StockApi stockApi;
    @Resource
    private RecordApi recordApi;

    //组装子表、附件列表的数据
    public SellOutboundVo getById(String id) {
        BizSellOutbound main = mainMapper.selectById(id);

        if (main == null) return null;

        SellOutboundVo vo = new SellOutboundVo(main);

        vo.setData(getSubById(id));
        vo.setImageList(recordApi.getAttachmentByPid(id));

        return vo;
    }

    //根据主表ID获取子表
    public List<BizSellOutboundSub> getSubById(String id) {
        return subMapper.selectList(
                Wrappers.lambdaQuery(BizSellOutboundSub.class)
                        .eq(BizSellOutboundSub::getPid, id)
        );
    }

    public PageResult<BizSellOutbound> search(SellOutboundSearch vo) {
        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(mainMapper.selectList(getSearchCondition(vo)));
    }

    public void export(SellOutboundSearch vo, HttpServletResponse response) throws Exception {
        List<SellOutboundExport> list = mainMapper.export(getSearchCondition(vo));
        ExcelUtil.exportSimply(list, response, "销售出库单导出");
    }

    @UserAction("'添加销售出库单'")
    @Transactional(rollbackFor = Exception.class)
    public R add(SellOutboundVo doc) {
        return addMain(doc);
    }

    @UserAction("'修改销售出库单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public R update(SellOutboundVo doc) {
        return updateMain(doc);
    }

    @UserAction("'提交销售出库单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public R commit(SellOutboundVo doc) {
        boolean isFirstCreate = StringUtils.isEmpty(doc.getId());
        R result = isFirstCreate ? addMain(doc) : updateMain(doc);

        if (result.isSuccess()) {
            historyMapper.insert(
                    BizDocHistory.builder()
                            .pid(doc.getId())
                            .type(DocHistoryEnum.COMMIT.getCode())
                            .uid(doc.getCid())
                            .uname(doc.getCname())
                            .statusBefore(DocStatusEnum.DRAFT.getCode())
                            .statusAfter(DocStatusEnum.WAIT_VERIFY.getCode())
                            .time(System.currentTimeMillis())
                            .build()
            );
            result.setMsg("提交成功");
        }
        else result.setMsg("提交失败，" + result.getMsg());

        return result;
    }

    @UserAction("'撤回销售出库单'+#vo.id")
    @Lock("#vo.id")
    @Transactional(rollbackFor = Exception.class)
    public R withdraw(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();

        if (rejectById(id) < 1) {
            return R.fail("撤回失败，请刷新重试");
        }

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.WITHDRAW.getCode())
                        .uid(user.getId())
                        .uname(user.getNickName())
                        .statusBefore(DocStatusEnum.WAIT_VERIFY.getCode())
                        .statusAfter(DocStatusEnum.DRAFT.getCode())
                        .time(System.currentTimeMillis())
                        .info(info)
                        .build()
        );

        return R.success("撤回成功");
    }

    @UserAction("'通过销售出库单'+#vo.id")
    @Lock({"#vo.pid", "#vo.id"})
    @Transactional(rollbackFor = Exception.class)
    public R pass(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();
        String pid = vo.getPid();
        long now = System.currentTimeMillis();

        List<BizSellOutboundSub> subList = getSubById(vo.getId());
        String err = check(vo.getPid(), subList);
        if (err != null) return R.fail("通过失败，" + err);

        if (1 > mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizSellOutbound.class)
                        .set(BizSellOutbound::getStatus, DocStatusEnum.VERIFIED.getCode())
                        .set(BizSellOutbound::getVid, user.getId())
                        .set(BizSellOutbound::getVname, user.getNickName())
                        .set(BizSellOutbound::getVtime, now)
                        .eq(BizSellOutbound::getId, id)
                        .eq(BizSellOutbound::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        )) {
            return R.fail("通过失败，请刷新重试");
        }

        //按分类分组统计出库数量
        List<StockOutboundVo> stockList = new ArrayList<>();
        Map<Integer, BigDecimal> outboundCount = new HashMap<>();
        for (BizSellOutboundSub outboundSub : subList) {
            stockList.add(new StockOutboundVo(outboundSub.getSid(), outboundSub.getNum()));

            Integer cid = outboundSub.getCid();
            BigDecimal num = outboundCount.getOrDefault(cid, BigDecimal.ZERO);
            outboundCount.put(cid, num.add(outboundSub.getNum()));
        }

        //更新销售订单子表的剩余未出库数量，记录销售订单的完成情况
        DocFinishEnum finish = DocFinishEnum.FINISHED;
        List<BizSellOrderSub> orderSubList = getOrderSubListByPid(pid);

        for (BizSellOrderSub orderSub : orderSubList) {
            if (orderSub.getRemainNum().equals(BigDecimal.ZERO)) {
                continue;
            }

            BigDecimal outboundNum = outboundCount.get(orderSub.getCid());

            if (outboundNum == null) continue;

            BigDecimal gap = orderSub.getRemainNum().subtract(outboundNum);

            //如果有任意一个采购商品的remainNum大于采购商品的num，则完成情况为进行中，否则为已完成
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                finish = DocFinishEnum.UNDERWAY;
            }

            //更新采购订单子表的remainNum
            orderSubMapper.update(
                    null,
                    Wrappers.lambdaUpdate(BizSellOrderSub.class)
                            .set(BizSellOrderSub::getRemainNum, gap)
                            .eq(BizSellOrderSub::getId, orderSub.getId())
            );
        }

        //更新销售订单完成情况
        if (1 > orderMapper.update(
                null,
                Wrappers.lambdaUpdate(BizSellOrder.class)
                        .set(BizSellOrder::getFinish, finish.getCode())
                        .set(BizSellOrder::getFtime, finish == DocFinishEnum.FINISHED ? now : null)
                        .eq(BizSellOrder::getId, pid)
                        .eq(BizSellOrder::getStatus, DocStatusEnum.VERIFIED.getCode())
        )) {
            throw new JsonResultException("通过失败，销售订单状态有误，请刷新重试");
        }

        //出库
        stockApi.outbound(stockList);

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.PASS.getCode())
                        .uid(user.getId())
                        .uname(user.getNickName())
                        .statusBefore(DocStatusEnum.WAIT_VERIFY.getCode())
                        .statusAfter(DocStatusEnum.VERIFIED.getCode())
                        .time(now)
                        .info(info)
                        .build()
        );

        return R.success("通过成功");
    }

    @UserAction("'驳回销售出库单'+#vo.id")
    @Lock("#vo.id")
    @Transactional(rollbackFor = Exception.class)
    public R reject(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();

        if (rejectById(id) < 1) {
            return R.fail("驳回失败，请刷新重试");
        }

        historyMapper.insert(
                BizDocHistory.builder()
                        .pid(id)
                        .type(DocHistoryEnum.REJECT.getCode())
                        .uid(user.getId())
                        .uname(user.getNickName())
                        .statusBefore(DocStatusEnum.WAIT_VERIFY.getCode())
                        .statusAfter(DocStatusEnum.DRAFT.getCode())
                        .time(System.currentTimeMillis())
                        .info(info)
                        .build()
        );

        return R.success("驳回成功");
    }

    @UserAction("'删除销售出库单'+#id")
    @Lock("#id")
    @Transactional(rollbackFor = Exception.class)
    public R del(String id) {
        if (mainMapper.deleteById(id) < 1) {
            return R.fail("删除失败");
        }

        //同时删除子表和附件
        delSubByPid(id);
        recordApi.delAttachmentByPid(id);

        return R.success("删除成功");
    }

    private R addMain(SellOutboundVo doc) {
        List<BizSellOutboundSub> subList = doc.getData();

        String err = check(doc.getPid(), subList);
        if (err != null) return R.fail(err);

        String id = DocUtil.getDocId("XSCK");

        if (StringUtils.isEmpty(id)) return R.fail("获取单号失败");

        doc.setId(id);

        //设置子表的pid
        subList.forEach(sub -> sub.setPid(id));

        mainMapper.insert(doc);
        subMapper.insertBatch(subList);

        //插入附件
        List<RecAttachment> uploadImageList = doc.getUploadImageList();
        Long time = System.currentTimeMillis();
        for (RecAttachment attachment : uploadImageList) {
            attachment.setPid(id);
            attachment.setTime(time);
        }
        recordApi.handleAttachment(uploadImageList, null);

        return R.success("添加成功", id);
    }

    private R updateMain(SellOutboundVo doc) {
        String docId = doc.getId();

        String err = checkUpdateStatus(docId);
        if (err == null) err = check(doc.getPid(), doc.getData());
        if (err != null) return R.fail(err);

        //更新主表
        mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizSellOutbound.class)
                        .set(BizSellOutbound::getPid, doc.getPid())
                        .set(BizSellOutbound::getStatus, doc.getStatus())
                        .set(BizSellOutbound::getRemark, doc.getRemark())
                        .eq(BizSellOutbound::getId, docId)
        );

        //删除旧的子表
        delSubByPid(docId);

        //插入新的子表
        subMapper.insertBatch(doc.getData());

        //附件增删
        List<RecAttachment> uploadImageList = doc.getUploadImageList();
        Long time = System.currentTimeMillis();
        for (RecAttachment attachment : uploadImageList) {
            attachment.setPid(docId);
            attachment.setTime(time);
        }
        recordApi.handleAttachment(uploadImageList, doc.getDeleteImageList());

        return R.success("修改成功");
    }

    //只有拟定状态的单据才能修改
    private String checkUpdateStatus(String id) {
        BizSellOutbound doc = mainMapper.selectById(id);
        if (doc == null || !doc.getStatus().equals(DocStatusEnum.DRAFT.getCode())) {
            return "单据状态已更新，请刷新后重试";
        }
        return null;
    }

    //添加、修改、通过前都需要检查
    private String check(String pid, List<BizSellOutboundSub> docSubList) {
        BizSellOrder order = orderMapper.selectById(pid);

        //检查销售订单是否已审核、未完成
        if (order == null
                || !order.getStatus().equals(DocStatusEnum.VERIFIED.getCode())
                || order.getFinish().equals(DocFinishEnum.FINISHED.getCode())
        ) {
            return "销售订单状态异常";
        }

        List<BizSellOrderSub> orderSubList = getOrderSubListByPid(pid);

        if (CollectionUtils.isEmpty(orderSubList)) {
            return "没有找到销售订单的数据";
        }

        //按分类统计商品的出库数量
        Map<Integer, BigDecimal> outboundCount = getOutboundCount(docSubList);

        String[] cids = new String[outboundCount.size()];

        int index = 0;

        //检查出库商品是否符合销售订单
        for (Integer cid : outboundCount.keySet()) {
            BizSellOrderSub orderSub = Util.find(orderSubList, t -> t.getCid().equals(cid));
            if (orderSub == null) {
                return "未在销售订单中找到对应的出库商品";
            }
            if (orderSub.getRemainNum().equals(BigDecimal.ZERO)) {
                return String.format("出库商品【%s】已全部出库", orderSub.getCname());
            }
            if (orderSub.getRemainNum().compareTo(outboundCount.get(cid)) < 0) {
                return String.format("出库商品【%s】的数量超出订单数量", orderSub.getCname());
            }
            cids[index] = String.valueOf(cid);
            index++;
        }

        //获取当前库存，判断库存是否足够
        List<BizStock> stockList = stockApi.getDetail(String.join(",", cids));
        for (BizSellOutboundSub sub : docSubList) {
            BizStock stock = Util.find(stockList, i -> i.getId().equals(sub.getSid()));
            if (stock == null || stock.getNum().compareTo(sub.getNum()) < 0) {
                return String.format("出库商品【%s】(采购入库单：%s)库存不足", sub.getCname(), sub.getPid());
            }
        }

        return null;
    }

    //根据主表ID删除子表
    private void delSubByPid(String pid) {
        subMapper.delete(
                Wrappers.lambdaQuery(BizSellOutboundSub.class)
                        .eq(BizSellOutboundSub::getPid, pid)
        );
    }

    //驳回单据，只有等待审核单据的才能被驳回
    private int rejectById(String id) {
        return mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizSellOutbound.class)
                        .set(BizSellOutbound::getStatus, DocStatusEnum.DRAFT.getCode())
                        .eq(BizSellOutbound::getId, id)
                        .eq(BizSellOutbound::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        );
    }

    private List<BizSellOrderSub> getOrderSubListByPid(String pid) {
        return orderSubMapper.selectList(
                Wrappers.lambdaQuery(BizSellOrderSub.class)
                        .eq(BizSellOrderSub::getPid, pid)
        );
    }

    private Wrapper<BizSellOutbound> getSearchCondition(SellOutboundSearch vo) {
        String pid = vo.getPid();
        String pidFuzzy = vo.getPidFuzzy();

        return DocUtil.baseCondition(BizSellOutbound.class, vo)
                .eq(pid != null, BizSellOutbound::getPid, pid)
                .like(!StringUtils.isEmpty(pidFuzzy), BizSellOutbound::getPid, pidFuzzy);
    }

    private Map<Integer, BigDecimal> getOutboundCount(List<BizSellOutboundSub> list) {
        if (CollectionUtils.isEmpty(list)) {
            return new HashMap<>();
        }
        return list
                .stream()
                .collect(
                        Collectors.groupingBy(
                                BizSellOutboundSub::getCid,
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        BizSellOutboundSub::getNum,
                                        BigDecimal::add
                                )
                        )
                );
    }
}