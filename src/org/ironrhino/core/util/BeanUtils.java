package org.ironrhino.core.util;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ironrhino.core.metadata.NotInCopy;
import org.ironrhino.core.model.BaseTreeableEntity;
import org.ironrhino.core.spring.converter.EnumToEnumConverter;
import org.ironrhino.core.spring.converter.EnumToIntegerConverter;
import org.ironrhino.core.spring.converter.IntegerToEnumConverterFactory;
import org.ironrhino.core.spring.converter.SerializableToSerializableConverter;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

public class BeanUtils {

	static DefaultConversionService conversionService = new DefaultConversionService();

	static {
		conversionService.addConverterFactory(new IntegerToEnumConverterFactory());
		conversionService.addConverter(Enum.class, Integer.class, new EnumToIntegerConverter((conversionService)));
		conversionService.addConverter(new EnumToEnumConverter());
		conversionService.addConverter(new SerializableToSerializableConverter());
	}

	public static boolean hasProperty(Class<?> clazz, String name) {
		if (org.springframework.beans.BeanUtils.getPropertyDescriptor(clazz, name) != null)
			return true;
		return false;
	}

	public static void copyPropertiesIfNotNull(Object source, Object target, String... properties) {
		if (properties.length == 0)
			return;
		BeanWrapperImpl bws = new BeanWrapperImpl(source);
		bws.setConversionService(conversionService);
		BeanWrapperImpl bwt = new BeanWrapperImpl(target);
		bwt.setConversionService(conversionService);
		for (String propertyName : properties) {
			Object value = bws.getPropertyValue(propertyName);
			if (value != null)
				bwt.setPropertyValue(propertyName, value);
		}
	}

	public static void copyProperties(Map<String, Object> source, Object target, String... ignoreProperties) {
		BeanWrapperImpl bw = new BeanWrapperImpl(target);
		bw.setConversionService(conversionService);
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			if (bw.isWritableProperty(entry.getKey()))
				bw.setPropertyValue(entry.getKey(), entry.getValue());
		}
	}

	public static void copyProperties(Object source, Object target, boolean ignoreNullValue, boolean ignoreNotInCopy,
			String... ignoreProperties) {
		Set<String> ignores = new HashSet<>();
		if (ignoreNotInCopy)
			ignores.addAll(AnnotationUtils.getAnnotatedPropertyNames(source.getClass(), NotInCopy.class));
		ignores.addAll(Arrays.asList(ignoreProperties));
		normalizeCollectionFields(source);
		BeanWrapperImpl bws = new BeanWrapperImpl(source);
		bws.setConversionService(conversionService);
		PropertyDescriptor[] sourcePds = bws.getPropertyDescriptors();
		BeanWrapperImpl bwt = new BeanWrapperImpl(target);
		bwt.setConversionService(conversionService);
		PropertyDescriptor[] targetPds = bwt.getPropertyDescriptors();
		for (PropertyDescriptor sourcePd : sourcePds) {
			if (sourcePd.getReadMethod() == null)
				continue;
			String name = sourcePd.getName();
			if (ignores.contains(name))
				continue;
			PropertyDescriptor targetPd = null;
			for (PropertyDescriptor pd : targetPds) {
				if (pd.getName().equals(name)) {
					targetPd = pd;
					break;
				}
			}
			if (targetPd == null || targetPd.getWriteMethod() == null)
				continue;
			Object value = bws.getPropertyValue(name);
			if (!ignoreNullValue || value != null)
				bwt.setPropertyValue(name, value);
		}
	}

	public static void copyProperties(Object source, Object target, boolean ignoreNotInCopy,
			String... ignoreProperties) {
		copyProperties(source, target, false, ignoreNotInCopy, ignoreProperties);
	}

	public static void copyProperties(Object source, Object target, String... ignoreProperties) {
		copyProperties(source, target, false, true, ignoreProperties);
	}

	public static <T extends BaseTreeableEntity<T>> T deepClone(T source, String... ignoreProperties) {
		return deepClone(source, null, ignoreProperties);
	}

	@SuppressWarnings("unchecked")
	public static <T extends BaseTreeableEntity<T>> T deepClone(T source, ObjectFilter filter,
			String... ignoreProperties) {
		if (filter != null && !filter.accept(source))
			throw new IllegalArgumentException("source object self must be accepted if you specify a filter");
		T ret = null;
		try {
			ret = (T) source.getClass().newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		copyProperties(source, ret, ignoreProperties);
		List<T> children = new ArrayList<T>();
		for (T child : source.getChildren()) {
			if (filter == null || filter.accept(child)) {
				T t = deepClone(child, filter, ignoreProperties);
				t.setParent(ret);
				children.add(t);
			}
		}
		ret.setChildren(children);
		return ret;

	}

	public static Object convert(Class<?> beanClass, String propertyName, String value) {
		try {
			return convert(beanClass.newInstance(), propertyName, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Object convert(Object bean, String propertyName, String value) {
		BeanWrapperImpl bw = new BeanWrapperImpl(bean);
		bw.setConversionService(conversionService);
		ConversionService cs = ApplicationContextUtils.getBean(ConversionService.class);
		if (cs != null)
			bw.setConversionService(cs);
		bw.setPropertyValue(propertyName, value);
		return bw.getPropertyValue(propertyName);
	}

	public static PropertyDescriptor getPropertyDescriptor(Class<?> beanClass, String propertyName) {
		if (propertyName.indexOf('.') == -1)
			return org.springframework.beans.BeanUtils.getPropertyDescriptor(beanClass, propertyName);
		String[] arr = propertyName.split("\\.");
		PropertyDescriptor pd = null;
		int i = 0;
		Class<?> clazz = beanClass;
		while (i < arr.length) {
			pd = BeanUtils.getPropertyDescriptor(clazz, arr[i]);
			if (pd == null)
				return null;
			clazz = pd.getPropertyType();
			i++;
		}
		return pd;
	}

	public static void setPropertyValue(Object bean, String propertyName, Object propertyValue) {
		BeanWrapperImpl bw = new BeanWrapperImpl(bean);
		bw.setConversionService(conversionService);
		if (propertyName.indexOf('.') == -1) {
			bw.setPropertyValue(propertyName, propertyValue);
			return;
		}
		String[] arr = propertyName.split("\\.");
		int i = 0;
		String name = null;
		while (i < arr.length - 1) {
			if (name == null)
				name = arr[i];
			else
				name += "." + arr[i];
			Object value = bw.getPropertyValue(name);
			if (value == null) {
				try {
					value = getPropertyDescriptor(bean.getClass(), name).getPropertyType().newInstance();
					bw.setPropertyValue(name, value);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			i++;
		}
		bw.setPropertyValue(propertyName, propertyValue);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void normalizeCollectionFields(Object bean) {
		if (!(bean instanceof Serializable))
			return;
		try {
			Class<?> clazz = bean.getClass();
			while (clazz != Object.class) {
				Field[] fields = clazz.getDeclaredFields();
				for (Field f : fields) {
					f.setAccessible(true);
					Object value = f.get(bean);
					if (value == null)
						continue;
					if (value.getClass().getName().startsWith("java."))
						continue;
					Class<?> declaredType = f.getType();
					if (declaredType == List.class) {
						List newValue = new ArrayList();
						newValue.addAll((List) value);
						f.set(bean, newValue);
					} else if (declaredType == Set.class) {
						Set newValue = new LinkedHashSet();
						newValue.addAll((Set) value);
						f.set(bean, newValue);
					} else if (declaredType == Map.class) {
						Map newValue = new LinkedHashMap();
						newValue.putAll((Map) value);
						f.set(bean, newValue);
					}
				}
				clazz = clazz.getSuperclass();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
