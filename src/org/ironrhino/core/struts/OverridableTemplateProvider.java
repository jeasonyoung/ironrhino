package org.ironrhino.core.struts;

import java.io.IOException;
import java.util.Locale;

import freemarker.template.Configuration;
import freemarker.template.Template;

public interface OverridableTemplateProvider {

	public void setConfiguration(Configuration configuration);

	public Template getTemplate(String name, Locale locale, String encoding, boolean parseAsFTL) throws IOException;

}
