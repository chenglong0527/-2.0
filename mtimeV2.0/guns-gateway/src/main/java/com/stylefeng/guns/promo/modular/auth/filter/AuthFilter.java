package com.stylefeng.guns.promo.modular.auth.filter;

import com.stylefeng.guns.core.base.tips.ErrorTip;
import com.stylefeng.guns.core.util.RenderUtil;
import com.stylefeng.guns.promo.modular.auth.util.JwtTokenUtil;
import com.stylefeng.guns.promo.common.exception.BizExceptionEnum;
import com.stylefeng.guns.promo.config.properties.JwtProperties;
import com.stylefeng.guns.service.user.beans.UserInfo;
import io.jsonwebtoken.JwtException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 对客户端请求的jwt token验证过滤器
 *
 * @author fengshuonan
 * @Date 2017/8/24 14:04
 */
public class AuthFilter extends OncePerRequestFilter {

    private final Log logger = LogFactory.getLog(this.getClass());

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    RedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String ignoreURI = jwtProperties.getIgnoreURI();
        String[] ignores = ignoreURI.split(",");
        String requestURI = request.getRequestURI();
        for (String ignore : ignores ) {
            if(requestURI.contains(ignore)){
                chain.doFilter(request, response);
                return;
            }
        }
        final String requestHeader = request.getHeader(jwtProperties.getHeader());
        String authToken = null;
        if (requestHeader != null && requestHeader.startsWith("Bearer ")) {
            authToken = requestHeader.substring(7);
            /*验证token是否过期,包含了验证jwt是否正确*/
            UserInfo userInfo = (UserInfo) redisTemplate.opsForValue().get(authToken);
            try {
                if(userInfo == null){
                    return;
                }else{
                    redisTemplate.expire(authToken, 1, TimeUnit.DAYS);
                    chain.doFilter(request, response);
                    return;
                }
            } catch (JwtException e) {
                //有异常就是token解析失败
                RenderUtil.renderJson(response, new ErrorTip(BizExceptionEnum.TOKEN_ERROR.getCode(), BizExceptionEnum.TOKEN_ERROR.getMessage()));
                return;
            }
        } else {
            //header没有带Bearer字段
            RenderUtil.renderJson(response, new ErrorTip(BizExceptionEnum.TOKEN_ERROR.getCode(), BizExceptionEnum.TOKEN_ERROR.getMessage()));
            return;
        }
//        chain.doFilter(request, response);
    }
}
