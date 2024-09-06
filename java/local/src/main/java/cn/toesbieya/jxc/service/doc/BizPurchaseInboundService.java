package cn.toesbieya.jxc.service.doc;

import cn.toesbieya.jxc.annoation.Lock;
import cn.toesbieya.jxc.annoation.UserAction;
import cn.toesbieya.jxc.enumeration.DocFinishEnum;
import cn.toesbieya.jxc.enumeration.DocHistoryEnum;
import cn.toesbieya.jxc.enumeration.DocStatusEnum;
import cn.toesbieya.jxc.exception.JsonResultException;
import cn.toesbieya.jxc.mapper.*;
import cn.toesbieya.jxc.model.entity.*;
import cn.toesbieya.jxc.model.vo.PurchaseInboundVo;
import cn.toesbieya.jxc.model.vo.R;
import cn.toesbieya.jxc.model.vo.UserVo;
import cn.toesbieya.jxc.model.vo.export.PurchaseInboundExport;
import cn.toesbieya.jxc.model.vo.result.PageResult;
import cn.toesbieya.jxc.model.vo.search.PurchaseInboundSearch;
import cn.toesbieya.jxc.model.vo.update.DocStatusUpdate;
import cn.toesbieya.jxc.service.RecService;
import cn.toesbieya.jxc.util.DocUtil;
import cn.toesbieya.jxc.util.ExcelUtil;
import cn.toesbieya.jxc.util.Util;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BizPurchaseInboundService {
    @Resource
    private BizPurchaseInboundMapper mainMapper;
    @Resource
    private BizPurchaseInboundSubMapper subMapper;
    @Resource
    private BizPurchaseOrderMapper orderMapper;
    @Resource
    private BizPurchaseOrderSubMapper orderSubMapper;
    @Resource
    private BizDocHistoryMapper historyMapper;
    @Resource
    private BizStockMapper stockMapper;
    @Resource
    private RecService recService;

    //组装子表、附件列表的数据
    public PurchaseInboundVo getById(String id) {
        BizPurchaseInbound main = mainMapper.selectById(id);

        if (main == null) return null;

        PurchaseInboundVo vo = new PurchaseInboundVo(main);

        vo.setData(getSubById(id));
        vo.setImageList(recService.getAttachmentByPid(id));

        return vo;
    }

    //根据主表ID获取子表
    public List<BizPurchaseInboundSub> getSubById(String id) {
        return subMapper.selectList(
                Wrappers.lambdaQuery(BizPurchaseInboundSub.class)
                        .eq(BizPurchaseInboundSub::getPid, id)
        );
    }

    public PageResult<BizPurchaseInbound> search(PurchaseInboundSearch vo) {
        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(mainMapper.selectList(getSearchCondition(vo)));
    }

    public void export(PurchaseInboundSearch vo, HttpServletResponse response) throws Exception {
        List<PurchaseInboundExport> list = mainMapper.export(getSearchCondition(vo));
        ExcelUtil.exportSimply(list, response, "采购入库单导出");
    }

    @UserAction("'添加采购入库单'")
    @Transactional(rollbackFor = Exception.class)
    public R add(PurchaseInboundVo doc) {
        return addMain(doc);
    }

    @UserAction("'修改采购入库单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public R update(PurchaseInboundVo doc) {
        return updateMain(doc);
    }

    @UserAction("'提交采购入库单'+#doc.id")
    @Lock("#doc.id")
    @Transactional(rollbackFor = Exception.class)
    public R commit(PurchaseInboundVo doc) {
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

    @UserAction("'撤回采购入库单'+#vo.id")
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

    @UserAction("'通过采购入库单'+#vo.id")
    @Lock({"#vo.pid", "#vo.id"})
    @Transactional(rollbackFor = Exception.class)
    public R pass(DocStatusUpdate vo, UserVo user) {
        String id = vo.getId();
        String info = vo.getInfo();
        String pid = vo.getPid();
        long now = System.currentTimeMillis();

        //检查采购订单状态、入库商品是否符合订单要求
        List<BizPurchaseInboundSub> subList = getSubById(id);
        String err = checkOrder(pid, subList);
        if (err != null) return R.fail("通过失败，" + err);

        if (1 > mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseInbound.class)
                        .set(BizPurchaseInbound::getStatus, DocStatusEnum.VERIFIED.getCode())
                        .set(BizPurchaseInbound::getVid, user.getId())
                        .set(BizPurchaseInbound::getVname, user.getNickName())
                        .set(BizPurchaseInbound::getVtime, now)
                        .eq(BizPurchaseInbound::getId, id)
                        .eq(BizPurchaseInbound::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        )) {
            return R.fail("通过失败，请刷新重试");
        }

        //修改采购订单完成情况、子表的未入库数量
        List<BizStock> stockList = new ArrayList<>();
        DocFinishEnum finish = DocFinishEnum.FINISHED;

        List<BizPurchaseOrderSub> orderSubList = getOrderSubListByPid(pid);

        //生成需要入库的商品列表
        for (BizPurchaseOrderSub orderSub : orderSubList) {
            if (orderSub.getRemainNum().equals(BigDecimal.ZERO)) {
                continue;
            }

            BizPurchaseInboundSub inboundSub = Util.find(subList, i -> i.getCid().equals(orderSub.getCid()));

            if (inboundSub == null) continue;

            stockList.add(
                    BizStock.builder()
                            .cid(inboundSub.getCid()).cname(inboundSub.getCname()).num(inboundSub.getNum())
                            .price(orderSub.getPrice()).ctime(now).cgddid(pid).cgrkid(id)
                            .build()
            );

            BigDecimal gap = orderSub.getRemainNum().subtract(inboundSub.getNum());

            //如果有任意一个采购商品的remainNum大于采购商品的num，则完成情况为进行中，否则为已完成
            if (gap.compareTo(BigDecimal.ZERO) > 0) {
                finish = DocFinishEnum.UNDERWAY;
            }

            //更新采购订单子表的remainNum
            orderSubMapper.update(
                    null,
                    Wrappers.lambdaUpdate(BizPurchaseOrderSub.class)
                            .set(BizPurchaseOrderSub::getRemainNum, gap)
                            .eq(BizPurchaseOrderSub::getId, orderSub.getId())
            );
        }

        //更新采购订单完成情况
        if (1 > orderMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseOrder.class)
                        .set(BizPurchaseOrder::getFinish, finish.getCode())
                        .set(BizPurchaseOrder::getFtime, finish == DocFinishEnum.FINISHED ? now : null)
                        .eq(BizPurchaseOrder::getId, pid)
                        .eq(BizPurchaseOrder::getStatus, DocStatusEnum.VERIFIED.getCode())
        )) {
            throw new JsonResultException("通过失败，采购订单状态有误，请刷新重试");
        }

        //插入库存
        if (stockList.isEmpty()) throw new JsonResultException("通过失败，入库异常");
        stockMapper.insertBatch(stockList);

        historyMapper.insert(
                BizDocHistory
                        .builder()
                        .pid(id).type(DocHistoryEnum.PASS.getCode())
                        .uid(user.getId()).uname(user.getNickName())
                        .statusBefore(DocStatusEnum.WAIT_VERIFY.getCode())
                        .statusAfter(DocStatusEnum.VERIFIED.getCode())
                        .time(now).info(info)
                        .build()
        );

        return R.success("通过成功");
    }

    @UserAction("'驳回采购入库单'+#vo.id")
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

    @UserAction("'删除采购入库单'+#id")
    @Lock("#id")
    @Transactional(rollbackFor = Exception.class)
    public R del(String id) {
        if (mainMapper.deleteById(id) < 1) {
            return R.fail("删除失败");
        }

        //同时删除子表和附件
        delSubByPid(id);
        recService.delAttachmentByPid(id);

        return R.success("删除成功");
    }

    private R addMain(PurchaseInboundVo doc) {
        List<BizPurchaseInboundSub> subList = doc.getData();

        String err = checkOrder(doc.getPid(), subList);
        if (err != null) return R.fail(err);

        String id = DocUtil.getDocId("CGRK");

        if (StringUtils.isEmpty(id)) {
            return R.fail("获取单号失败");
        }

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
        recService.handleAttachment(uploadImageList, null);

        return R.success("添加成功", id);
    }

    private R updateMain(PurchaseInboundVo doc) {
        String docId = doc.getId();

        String err = checkUpdateStatus(docId);
        if (err == null) err = checkOrder(doc.getPid(), doc.getData());
        if (err != null) return R.fail(err);

        //更新主表
        mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseInbound.class)
                        .set(BizPurchaseInbound::getPid, doc.getPid())
                        .set(BizPurchaseInbound::getStatus, doc.getStatus())
                        .set(BizPurchaseInbound::getRemark, doc.getRemark())
                        .eq(BizPurchaseInbound::getId, docId)
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
        recService.handleAttachment(uploadImageList, doc.getDeleteImageList());

        return R.success("修改成功");
    }

    //只有拟定状态的单据才能修改
    private String checkUpdateStatus(String id) {
        BizPurchaseInbound doc = mainMapper.selectById(id);
        if (doc == null || !doc.getStatus().equals(DocStatusEnum.DRAFT.getCode())) {
            return "单据状态已更新，请刷新后重试";
        }
        return null;
    }

    //检查采购订单是否已审核、未完成、还有未入库的商品
    private String checkOrder(String pid, List<BizPurchaseInboundSub> docSubList) {
        BizPurchaseOrder order = orderMapper.selectById(pid);

        if (order == null
                || !order.getStatus().equals(DocStatusEnum.VERIFIED.getCode())
                || order.getFinish().equals(DocFinishEnum.FINISHED.getCode())
        ) {
            return "采购订单状态异常";
        }

        List<BizPurchaseOrderSub> orderSubList = getOrderSubListByPid(pid);

        if (CollectionUtils.isEmpty(orderSubList)) return "没有找到采购订单的数据";

        //判断入库商品是否合法
        for (BizPurchaseInboundSub inboundSub : docSubList) {
            BizPurchaseOrderSub orderSub = Util.find(orderSubList, t -> t.getCid().equals(inboundSub.getCid()));

            if (orderSub == null) {
                return String.format("入库商品【%s】不在采购订单中", inboundSub.getCname());
            }
            if (orderSub.getRemainNum().compareTo(BigDecimal.ZERO) <= 0) {
                return String.format("入库商品【%s】已全部入库", inboundSub.getCname());
            }
            if (orderSub.getRemainNum().compareTo(inboundSub.getNum()) < 0) {
                return String.format("入库商品【%s】的数量超出订单数量", inboundSub.getCname());
            }
        }
        return null;
    }

    //根据主表ID删除子表
    private void delSubByPid(String pid) {
        subMapper.delete(
                Wrappers.lambdaQuery(BizPurchaseInboundSub.class)
                        .eq(BizPurchaseInboundSub::getPid, pid)
        );
    }

    //驳回单据，只有等待审核单据的才能被驳回
    private int rejectById(String id) {
        return mainMapper.update(
                null,
                Wrappers.lambdaUpdate(BizPurchaseInbound.class)
                        .set(BizPurchaseInbound::getStatus, DocStatusEnum.DRAFT.getCode())
                        .eq(BizPurchaseInbound::getId, id)
                        .eq(BizPurchaseInbound::getStatus, DocStatusEnum.WAIT_VERIFY.getCode())
        );
    }

    private List<BizPurchaseOrderSub> getOrderSubListByPid(String pid) {
        return orderSubMapper.selectList(
                Wrappers.lambdaQuery(BizPurchaseOrderSub.class)
                        .eq(BizPurchaseOrderSub::getPid, pid)
        );
    }

    private Wrapper<BizPurchaseInbound> getSearchCondition(PurchaseInboundSearch vo) {
        String pid = vo.getPid();
        String pidFuzzy = vo.getPidFuzzy();

        return DocUtil.baseCondition(BizPurchaseInbound.class, vo)
                .eq(pid != null, BizPurchaseInbound::getPid, pid)
                .like(!StringUtils.isEmpty(pidFuzzy), BizPurchaseInbound::getPid, pidFuzzy);
    }
}
