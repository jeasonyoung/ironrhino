package org.ironrhino.security.component;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.ironrhino.core.spring.security.DefaultAuthenticationFailureHandler;
import org.ironrhino.core.struts.I18N;
import org.ironrhino.security.model.LoginRecord;
import org.ironrhino.security.service.UserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.hibernate4.HibernateCallback;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuthenticationFailureHandler extends DefaultAuthenticationFailureHandler {

	@Autowired
	private UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter;

	@Autowired
	private UserManager userManager;

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException e) throws IOException, ServletException {
		super.onAuthenticationFailure(request, response, e);
		LoginRecord loginRecord = new LoginRecord();
		loginRecord.setUsername(request.getParameter(usernamePasswordAuthenticationFilter.getUsernameParameter()));
		loginRecord.setAddress(request.getRemoteAddr());
		loginRecord.setFailed(true);
		loginRecord.setCause(I18N.getText(e.getClass().getName()));
		if (loginRecord.getUsername() != null)
			save(loginRecord);
	}

	private void save(final LoginRecord loginRecord) {
		userManager.execute(new HibernateCallback<LoginRecord>() {
			@Override
			public LoginRecord doInHibernate(Session session) throws HibernateException {
				session.save(loginRecord);
				return null;
			}
		});
	}
}
