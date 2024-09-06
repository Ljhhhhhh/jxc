package cn.toesbieya.jxc.web.common.aspect;

import cn.toesbieya.jxc.api.RecordApi;
import cn.toesbieya.jxc.common.exception.JsonResultException;
import cn.toesbieya.jxc.common.model.entity.RecUserAction;
import cn.toesbieya.jxc.common.model.vo.R;
import cn.toesbieya.jxc.common.util.Util;
import cn.toesbieya.jxc.web.common.util.ThreadUtil;
import cn.toesbieya.jxc.web.common.util.WebUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class RestExceptionAspect {
    @Reference
    private RecordApi recordApi;

    //抛出异常时希望返回给前台
    @ExceptionHandler(JsonResultException.class)
    public R handle(JsonResultException e) {
        recordUserAction(e);
        return R.fail(e.getMessage());
    }

    //请求方法有误
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R handle(HttpRequestMethodNotSupportedException e) {
        return R.fail("请求方法有误");
    }

    //校验不通过
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String error = bindingResult.getFieldErrors().get(0).getDefaultMessage();
        return R.fail(error);
    }

    //@RequestParam没有匹配到值
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R handle(MissingServletRequestParameterException e) {
        log.error(e.getMessage());
        return R.fail("get参数有误");
    }

    //@RequestBody没有匹配到值
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R handle(HttpMessageNotReadableException e) {
        log.error(e.getMessage());
        return R.fail("post参数有误");
    }

    //上传文件过大
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R handle(MaxUploadSizeExceededException e) {
        log.error("上传的文件超出限定大小,{}", e.getMessage());
        recordUserAction(e);
        return R.fail("上传的文件过大");
    }

    //最终捕获
    @ExceptionHandler(Exception.class)
    public R handle(Exception e) {
        log.error(String.format("服务运行异常,访问路径：%s", WebUtil.getRequest().getServletPath()), e);
        recordUserAction(e);
        return R.fail("服务运行异常");
    }

    private void recordUserAction(Exception e) {
        RecUserAction action = ThreadUtil.getAction();
        if (action != null && !StringUtils.isEmpty(action.getAction())) {
            action.setTime(System.currentTimeMillis());
            action.setError(Util.exception2Str(e));
            action.setSuccess(false);
            recordApi.insertUserAction(action);
        }
        ThreadUtil.clearAll();
    }
}
