package org.ironrhino.core.spring.http.client;

import javax.annotation.PostConstruct;

import org.ironrhino.core.util.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.AsyncClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;

@Component
public class AsyncRestTemplate extends org.springframework.web.client.AsyncRestTemplate {

	@Value("${restTemplate.connectTimeout:5000}")
	private int connectTimeout;

	@Value("${restTemplate.readTimeout:5000}")
	private int readTimeout;

	@Value("${restTemplate.trustAllHosts:false}")
	private boolean trustAllHosts;

	public AsyncRestTemplate() {
		super();
		setAsyncRequestFactory(new SimpleClientHttpRequestFactory(trustAllHosts));
	}

	public AsyncRestTemplate(AsyncClientHttpRequestFactory requestFactory) {
		super();
		setAsyncRequestFactory(requestFactory);
	}

	@PostConstruct
	public void init() {
		AsyncClientHttpRequestFactory chrf = getAsyncRequestFactory();
		if (chrf instanceof org.springframework.http.client.SimpleClientHttpRequestFactory) {
			org.springframework.http.client.SimpleClientHttpRequestFactory scrf = (org.springframework.http.client.SimpleClientHttpRequestFactory) chrf;
			scrf.setConnectTimeout(connectTimeout);
			scrf.setReadTimeout(readTimeout);
		}
		MappingJackson2HttpMessageConverter jackson2 = null;
		for (HttpMessageConverter<?> hmc : getMessageConverters()) {
			if (hmc instanceof MappingJackson2HttpMessageConverter) {
				jackson2 = (MappingJackson2HttpMessageConverter) hmc;
				break;
			}
		}
		if (jackson2 == null) {
			jackson2 = new MappingJackson2HttpMessageConverter();
			getMessageConverters().add(jackson2);
		}
		jackson2.setObjectMapper(JsonUtils.createNewObjectMapper());
	}
}
