package com.myForum.aspect;

import com.myForum.annotation.GlobalInterceptor;
import com.myForum.annotation.VerifyParam;
import com.myForum.entity.constants.Constants;
import com.myForum.entity.dto.SessionWebUserDto;
import com.myForum.entity.dto.SysSettingDto;
import com.myForum.entity.enums.DateTimePatternEnum;
import com.myForum.entity.enums.ResponseCodeEnum;
import com.myForum.entity.enums.UserOperaFrequencyTypeEnum;
import com.myForum.entity.query.ForumArticleQuery;
import com.myForum.entity.query.ForumCommentQuery;
import com.myForum.entity.query.LikeRecordQuery;
import com.myForum.entity.vo.ResponseVO;
import com.myForum.exception.BusinessException;
import com.myForum.service.ForumArticleService;
import com.myForum.service.ForumCommentService;
import com.myForum.service.LikeRecordService;
import com.myForum.utils.DateUtil;
import com.myForum.utils.StringTools;
import com.myForum.utils.SysCacheUtils;
import com.myForum.utils.VerifyUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Date;

@Aspect
@Component
public class OperationAspectWeb {

    @Resource
    private ForumArticleService forumArticleService;

    @Resource
    private ForumCommentService forumCommentService;

    @Resource
    private LikeRecordService likeRecordService;

    private static final Logger logger = LoggerFactory.getLogger(OperationAspectWeb.class);

    private static final String[] TYPE_BASE = {"java.lang.String", "java.lang.Integer", "java.lang.Long"};

    @Pointcut("@annotation(com.myForum.annotation.GlobalInterceptor)")
    private void requestInterceptor() {

    }

    @Around("requestInterceptor()")
    private Object interceptorDo(ProceedingJoinPoint point) {
        try {
            //取出切面方法对应的数据以及注解
            Object target = point.getTarget();
            Object[] arguments = point.getArgs();
            String methodName = point.getSignature().getName();
            Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            //没有注解
            if (interceptor == null) {
                return null;
            }
            //校验登录
            if (interceptor.checkLogin()) {
                checkLogin();
            }
            //校验参数
            if (interceptor.checkParams()) {
                checkParams(method, arguments);
            }
            //校验频率
            this.checkFrequency(interceptor.frequencyType());

            //执行操作
            Object pointResult = point.proceed();

            //增加频次限制
            if (pointResult instanceof ResponseVO) {
                ResponseVO responseVO = (ResponseVO) pointResult;
                if (Constants.STATUC_SUCCESS.equals(responseVO.getStatus())) {
                    addOpCount(interceptor.frequencyType());
                }
            }
            return pointResult;
        } catch (BusinessException e) {
            logger.error("全局拦截器异常", e);
            throw e;
        } catch (Exception e) {
            logger.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        } catch (Throwable e) {
            logger.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    /**
     * 校验登录
     */
    private void checkLogin() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        Object obj = session.getAttribute(Constants.SESSION_KEY);
        if (obj == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }
    }

    /**
     * 校验参数
     *
     * @param m         方法
     * @param arguments 参数值
     */
    private void checkParams(Method m, Object[] arguments) {
        //参数
        Parameter[] parameters = m.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object value = arguments[i];
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            if (verifyParam == null) {
                continue;
            }
            //如果传递的是值
            if (ArrayUtils.contains(TYPE_BASE, parameter.getParameterizedType().getTypeName())) {
                checkValue(value, verifyParam);
            } else {//如果传递的是对象
                checkObjValue(parameter, value);
            }
        }
    }

    //对象校验
    private void checkObjValue(Parameter parameter, Object value) {
        try {
            String typeName = parameter.getParameterizedType().getTypeName();
            Class classZ = Class.forName(typeName);
            Field[] fields = classZ.getDeclaredFields();
            for (Field field : fields) {
                VerifyParam fieldVerifyParam = field.getAnnotation(VerifyParam.class);
                if (fieldVerifyParam == null) {
                    continue;
                }
                field.setAccessible(true);
                Object resultValue = field.get(value);
                checkValue(resultValue, fieldVerifyParam);
            }
        } catch (BusinessException e) {
            logger.error("校验参数失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("校验参数失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    //参数值校验
    private void checkValue(Object value, VerifyParam verifyParam) {
        //value(参数值)是否为空
        Boolean isEmpty = value == null || StringTools.isEmpty(value.toString());
        //value(参数值)长度
        Integer length = value == null ? 0 : value.toString().length();

        //校验空
        if (isEmpty && verifyParam.required()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //校验长度
        if (!isEmpty && (verifyParam.max() != -1 && verifyParam.max() < length || verifyParam.min() != -1 && verifyParam.min() > length)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //校验正则
        if (!isEmpty && !StringTools.isEmpty(verifyParam.regex().getRegex()) &&
                !VerifyUtils.verify(verifyParam.regex().getRegex(), String.valueOf(value))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    /**
     * 校验频率
     *
     * @param typeEnum
     */
    private void checkFrequency(UserOperaFrequencyTypeEnum typeEnum) {
        if (typeEnum == null || UserOperaFrequencyTypeEnum.NO_CHECK == typeEnum) {
            return;
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        SessionWebUserDto userDto = (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
        String curDate = DateUtil.format(new Date(), DateTimePatternEnum.YYYY_MM_DD.getPattern());
        String sessionKey = Constants.SESSION_KEY_FREQUENCY + curDate + typeEnum.getOperaType();
        Integer count = (Integer) session.getAttribute(sessionKey);
        SysSettingDto sysSettingDto = SysCacheUtils.getSysSetting();
        switch (typeEnum) {
            case POST_ARTICLE:
                if (count == null) {
                    ForumArticleQuery forumArticleQuery = new ForumArticleQuery();
                    forumArticleQuery.setUserId(userDto.getUserId());
                    forumArticleQuery.setPostTimeStart(curDate);
                    forumArticleQuery.setPostTimeEnd(curDate);
                    count = forumArticleService.findCountByParam(forumArticleQuery);
                }
                if (count >= sysSettingDto.getPostSetting().getPostDayCountThreshold()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
                break;
            case POST_COMMENT:
                if (count == null) {
                    ForumCommentQuery forumCommentQuery = new ForumCommentQuery();
                    forumCommentQuery.setUserId(userDto.getUserId());
                    forumCommentQuery.setPostTimeStart(curDate);
                    forumCommentQuery.setPostTimeEnd(curDate);
                    count = forumCommentService.findCountByParam(forumCommentQuery);
                }

                if (count >= sysSettingDto.getCommentSetting().getCommentDayCountThreshold()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
                break;
            case DO_LIKE:
                if (count == null) {
                    LikeRecordQuery recordQuery = new LikeRecordQuery();
                    recordQuery.setUserId(userDto.getUserId());
                    recordQuery.setCreateTimeStart(curDate);
                    recordQuery.setCreateTimeEnd(curDate);
                    count = likeRecordService.findCountByParam(recordQuery);

                }
                if (count >= sysSettingDto.getLikeSetting().getLikeDayCountThreshold()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
                break;
            case IMAGE_UPL0AD:
                if (count == null) {
                    count = 0;
                }
                if (count >= sysSettingDto.getPostSetting().getDayImageUploadCount()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
                break;
        }
        session.setAttribute(sessionKey, count);
    }

    //统计已经统计数据
    private void addOpCount(UserOperaFrequencyTypeEnum typeEnum) {
        if (typeEnum == null || typeEnum == UserOperaFrequencyTypeEnum.NO_CHECK) {
            return;
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        String curDate = DateUtil.format(new Date(), DateTimePatternEnum.YYYY_MM_DD.getPattern());
        String sessionKey = Constants.SESSION_KEY_FREQUENCY + curDate + typeEnum.getOperaType();
        Integer count = (Integer) session.getAttribute(sessionKey);
        session.setAttribute(sessionKey, count + 1);
    }

}
