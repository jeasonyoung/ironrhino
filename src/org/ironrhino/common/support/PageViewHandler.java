package org.ironrhino.common.support;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ironrhino.common.service.PageViewService;
import org.ironrhino.core.servlet.AccessHandler;
import org.ironrhino.core.session.HttpSessionManager;
import org.ironrhino.core.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PageViewHandler extends AccessHandler {

	@Autowired(required = false)
	private PageViewService pageViewService;

	@Autowired(required = false)
	private ExecutorService executorService;

	@Autowired
	private HttpSessionManager httpSessionManager;

	@Override
	public boolean handle(final HttpServletRequest request, HttpServletResponse response) {
		if (pageViewService != null && request.getMethod().equalsIgnoreCase("GET")
				&& !RequestUtils.isInternalTesting(request) && !request.getRequestURI().startsWith("/assets/")
				&& !request.getRequestURI().endsWith("/favicon.ico")) {
			final String remoteAddr = request.getRemoteAddr();
			final String requestURL = request.getRequestURL().toString();
			final String sessionId = httpSessionManager.getSessionId(request);
			String str = RequestUtils.getCookieValue(request, "U");
			if (str == null)
				str = RequestUtils.getCookieValue(request, "UU");
			final String username = str;
			final String referer = request.getHeader("Referer");
			Runnable task = new Runnable() {
				@Override
				public void run() {
					pageViewService.put(new Date(), remoteAddr, requestURL, sessionId, username, referer);
				}
			};
			if (executorService != null)
				executorService.execute(task);
			else
				task.run();
		}
		return false;
	}
}
