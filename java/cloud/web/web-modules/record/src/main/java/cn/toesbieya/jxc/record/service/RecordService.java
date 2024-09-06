package cn.toesbieya.jxc.record.service;

import cn.toesbieya.jxc.api.FileApi;
import cn.toesbieya.jxc.api.RecordApi;
import cn.toesbieya.jxc.common.model.entity.RecAttachment;
import cn.toesbieya.jxc.common.model.entity.RecLoginHistory;
import cn.toesbieya.jxc.common.model.entity.RecUserAction;
import cn.toesbieya.jxc.record.mapper.RecAttachmentMapper;
import cn.toesbieya.jxc.record.mapper.RecLoginHistoryMapper;
import cn.toesbieya.jxc.record.mapper.RecUserActionMapper;
import cn.toesbieya.jxc.record.model.vo.LoginHistorySearch;
import cn.toesbieya.jxc.record.model.vo.UserActionSearch;
import cn.toesbieya.jxc.web.common.model.vo.PageResult;
import cn.toesbieya.jxc.web.common.util.IpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@org.apache.dubbo.config.annotation.Service
public class RecordService implements RecordApi {
    @Resource
    private RecLoginHistoryMapper loginHistoryMapper;
    @Resource
    private RecUserActionMapper userActionMapper;
    @Resource
    private RecAttachmentMapper attachmentMapper;
    @Reference
    private FileApi fileApi;

    public PageResult<RecLoginHistory> searchLoginHistory(LoginHistorySearch vo) {
        Integer uid = vo.getUid();
        String uname = vo.getUname();
        Boolean login = vo.getLogin();
        String ip = vo.getIp();
        String address = vo.getAddress();
        Long startTime = vo.getStartTime();
        Long endTime = vo.getEndTime();

        Wrapper<RecLoginHistory> wrapper =
                Wrappers.lambdaQuery(RecLoginHistory.class)
                        .eq(uid != null, RecLoginHistory::getUid, uid)
                        .like(!StringUtils.isEmpty(uname), RecLoginHistory::getUname, vo.getUname())
                        .eq(login != null, RecLoginHistory::isLogin, login)
                        .eq(!StringUtils.isEmpty(ip), RecLoginHistory::getIp, vo.getIp())
                        .like(!StringUtils.isEmpty(address), RecLoginHistory::getAddress, vo.getAddress())
                        .ge(startTime != null, RecLoginHistory::getTime, startTime)
                        .le(endTime != null, RecLoginHistory::getTime, endTime)
                        .orderByDesc(RecLoginHistory::getTime);

        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(loginHistoryMapper.selectList(wrapper));
    }

    public PageResult<RecUserAction> searchUserAction(UserActionSearch vo) {
        Integer uid = vo.getUid();
        String uname = vo.getUname();
        Boolean success = vo.getSuccess();
        String ip = vo.getIp();
        String url = vo.getUrl();
        Long startTime = vo.getStartTime();
        Long endTime = vo.getEndTime();

        Wrapper<RecUserAction> wrapper =
                Wrappers.lambdaQuery(RecUserAction.class)
                        .eq(uid != null, RecUserAction::getUid, uid)
                        .like(!StringUtils.isEmpty(uname), RecUserAction::getUname, uname)
                        .eq(success != null, RecUserAction::isSuccess, success)
                        .eq(!StringUtils.isEmpty(ip), RecUserAction::getIp, ip)
                        .eq(!StringUtils.isEmpty(url), RecUserAction::getUrl, url)
                        .ge(startTime != null, RecUserAction::getTime, startTime)
                        .le(endTime != null, RecUserAction::getTime, endTime)
                        .orderByDesc(RecUserAction::getTime);

        PageHelper.startPage(vo.getPage(), vo.getPageSize());
        return new PageResult<>(userActionMapper.selectList(wrapper));
    }

    @Override
    public List<RecAttachment> getAttachmentByPid(String pid) {
        if (StringUtils.isEmpty(pid)) {
            return Collections.emptyList();
        }

        return attachmentMapper.selectList(
                Wrappers.lambdaQuery(RecAttachment.class)
                        .eq(RecAttachment::getPid, pid)
                        .orderByAsc(RecAttachment::getSort)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAttachment(List<RecAttachment> upload, List<String> delete) {
        if (!CollectionUtils.isEmpty(upload)) {
            attachmentMapper.insertBatch(upload);
        }
        if (!CollectionUtils.isEmpty(delete)) {
            attachmentMapper.delete(
                    Wrappers.lambdaQuery(RecAttachment.class)
                            .in(RecAttachment::getUrl, delete)
            );
            fileApi.deleteBatch(delete);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delAttachmentByPid(String pid) {
        List<RecAttachment> list = attachmentMapper.selectList(
                Wrappers.lambdaQuery(RecAttachment.class)
                        .eq(RecAttachment::getPid, pid)
        );

        if (CollectionUtils.isEmpty(list)) return;

        attachmentMapper.delete(Wrappers.lambdaQuery(RecAttachment.class).eq(RecAttachment::getPid, pid));

        List<String> urls = list.stream().map(RecAttachment::getUrl).collect(Collectors.toList());
        fileApi.deleteBatch(urls);
    }

    @Override
    @Async("dbInsertExecutor")
    public void insertLoginHistory(RecLoginHistory history) {
        if (StringUtils.isEmpty(history.getAddress())) {
            history.setAddress(IpUtil.getIpAddress(history.getIp()));
        }
        history.setId(null);
        loginHistoryMapper.insert(history);
    }

    @Override
    @Async("dbInsertExecutor")
    public void insertUserAction(RecUserAction action) {
        action.setId(null);
        userActionMapper.insert(action);
    }
}
