package org.ironrhino.security.component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.ironrhino.core.servlet.AccessHandler;
import org.ironrhino.core.session.HttpSessionManager;
import org.ironrhino.core.util.RequestUtils;
import org.ironrhino.security.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

public class SsoHandler extends AccessHandler {

	@Value("${ssoHandler.pattern:}")
	protected String pattern;

	@Value("${ssoHandler.excludePattern:}")
	protected String excludePattern;

	@Value("${httpSessionManager.sessionTrackerName:" + HttpSessionManager.DEFAULT_SESSION_TRACKER_NAME + "}")
	protected String sessionTrackerName = HttpSessionManager.DEFAULT_SESSION_TRACKER_NAME;

	@Value("${httpSessionManager.sessionCookieName:" + HttpSessionManager.DEFAULT_SESSION_COOKIE_NAME + "}")
	protected String sessionCookieName = HttpSessionManager.DEFAULT_SESSION_COOKIE_NAME;

	@Value("${portal.baseUrl:http://www.example.com}")
	protected String portalBaseUrl;

	@Value("${portal.api.user.self.url:/api/user/@self}")
	protected String portalApiUserSelfUrl;

	@Value("${portal.login.url:/login}")
	protected String portalLoginUrl;

	@Autowired
	protected UserDetailsService userDetailsService;

	@Autowired
	protected RestTemplate restTemplate;

	@Override
	public String getPattern() {
		return pattern;
	}

	@Override
	public String getExcludePattern() {
		return excludePattern;
	}

	@Override
	public boolean handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!RequestUtils.isSameOrigin(request.getRequestURL().toString(), portalBaseUrl))
			return false;
		String token = RequestUtils.getCookieValue(request, sessionTrackerName);
		if (StringUtils.isBlank(token)) {
			redirect(request, response);
			return true;
		} else {
			URI apiUri;
			try {
				apiUri = new URI(portalApiUserSelfUrl.indexOf("://") > 0 ? portalApiUserSelfUrl
						: portalBaseUrl + portalApiUserSelfUrl);
			} catch (URISyntaxException e) {
				e.printStackTrace();
				return true;
			}
			StringBuilder cookie = new StringBuilder();
			cookie.append(sessionTrackerName).append("=").append(URLEncoder.encode(token, "UTF-8"));
			String session = RequestUtils.getCookieValue(request, sessionCookieName);
			if (session != null)
				cookie.append("; ").append(sessionCookieName).append("=").append(URLEncoder.encode(session, "UTF-8"));
			RequestEntity<?> requestEntity = RequestEntity.get(apiUri).header("Cookie", cookie.toString())
					.header("X-Real-IP", request.getRemoteAddr()).build();
			try {
				User userFromApi = restTemplate.exchange(requestEntity, User.class).getBody();
				UserDetails ud = map(userFromApi);
				SecurityContext sc = SecurityContextHolder.getContext();
				sc.setAuthentication(
						new UsernamePasswordAuthenticationToken(ud, ud.getPassword(), ud.getAuthorities()));
				Map<String, Object> sessionMap = new HashMap<>(2, 1);
				sessionMap.put(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);
				request.setAttribute(HttpSessionManager.REQUEST_ATTRIBUTE_KEY_SESSION_MAP_FOR_API, sessionMap);
			} catch (HttpClientErrorException e) {
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					redirect(request, response);
					return true;
				}
				throw e;
			}
		}
		return false;

	}

	protected void redirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
		StringBuffer sb = request.getRequestURL();
		String queryString = request.getQueryString();
		if (StringUtils.isNotBlank(queryString))
			sb.append("?").append(queryString);
		String targetUrl = sb.toString();
		StringBuilder redirectUrl = new StringBuilder(portalLoginUrl.indexOf("://") > 0 ? "" : portalBaseUrl);
		redirectUrl.append(portalLoginUrl).append(portalLoginUrl.indexOf('?') > 0 ? '&' : '?');
		redirectUrl.append("targetUrl=").append(URLEncoder.encode(targetUrl, "UTF-8"));
		response.sendRedirect(redirectUrl.toString());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected UserDetails map(User userFromApi) {
		UserDetails user = userDetailsService.loadUserByUsername(userFromApi.getUsername());
		Collection authorities = user.getAuthorities();
		if (userFromApi.getRoles() != null) {
			for (String role : userFromApi.getRoles()) {
				GrantedAuthority ga = new SimpleGrantedAuthority(role);
				if (!authorities.contains(ga))
					authorities.add(ga);
			}
		}
		return user;
	}

}
