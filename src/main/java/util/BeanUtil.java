package util;

import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BeanUtil {

    private static ConcurrentMap<String, BeanCopier> beanCopierMap = new ConcurrentHashMap<>();
    private static final Set<Class<?>> wrapClassMap = new HashSet<>(9);

    static {
        wrapClassMap.add(Boolean.class);
        wrapClassMap.add(Byte.class);
        wrapClassMap.add(Character.class);
        wrapClassMap.add(Double.class);
        wrapClassMap.add(Float.class);
        wrapClassMap.add(Integer.class);
        wrapClassMap.add(Long.class);
        wrapClassMap.add(Short.class);
    }

    public static <T> T convert(Object source, Class<T> targetCls){
        T target = null;
        if(source != null){
            try {
                target = targetCls.newInstance();
            }catch (Exception e){
                e.printStackTrace();
            }
            BeanCopier beanCopier = getBeanCopier(source.getClass(), targetCls);
            beanCopier.copy(source, target, new DeepCopyConvert(targetCls));
        }
        return target;
    }

    public static class DeepCopyConvert implements Converter{

        private Class<?> target;

        public DeepCopyConvert(Class<?> target) {
            this.target = target;
        }

        @Override
        public Object convert(Object sourceVal, Class targetCls, Object targetMethodName) {
            if(sourceVal == null){
                return null;
            }
            // List
            if(sourceVal instanceof List){
                List values = (List) sourceVal;
                List rsList = new ArrayList(values.size());
                for(Object val : values){
                    String tempFieldName = targetMethodName.toString().replace("set", "");
                    String fieldName = tempFieldName.substring(0, 1).toLowerCase() + tempFieldName.substring(1);
                    // 获取List中元素类型
                    Class clazz = getElementType(target, fieldName);
                    // 是否为原始类型（boolean、char、byte、short、int、long、float、double）
                    if(clazz.isPrimitive()){
                        rsList.add(val);
                    } else if(isWrapClass(clazz)){
                        rsList.add(val);
                    }else{
                        rsList.add(BeanUtil.convert(val, clazz));
                    }
                }
                return rsList;
            }
            // Map
            if(sourceVal instanceof Map){
                // TODO
                return sourceVal;
            }
            // 数组
            if(targetCls.isArray()){
                return sourceVal;
            }
            // 包装类型
            if(isWrapClass(targetCls)){
                return sourceVal;
            }
            // String
            if(sourceVal instanceof String){
                return new StringBuilder((String) sourceVal).toString();
            }
            // Date
            if(sourceVal instanceof Date){
                Date d = (Date) sourceVal;
                return new Date(d.getTime());
            }
            // 引用类型
            if(!targetCls.isPrimitive()){
                return BeanUtil.convert(sourceVal, targetCls);
            }
            return sourceVal;
        }
    }

    public static BeanCopier getBeanCopier(Class<?> sourceCls, Class<?> targetCls){
        String beanCopierKey = generateBeanKey(sourceCls, targetCls);
        if (beanCopierMap.containsKey(beanCopierKey)) {
            return beanCopierMap.get(beanCopierKey);
        } else {
            BeanCopier beanCopier = BeanCopier.create(sourceCls, targetCls, true);
            beanCopierMap.putIfAbsent(beanCopierKey, beanCopier);
        }
        return beanCopierMap.get(beanCopierKey);

    }


    public static String generateBeanKey(Class<?> source, Class<?> target) {
        return source.getName() + "@" + target.getName();
    }


    public static boolean isWrapClass(Class<?> clazz){
        if(wrapClassMap.contains(clazz)){
            return true;
        }
        return false;
    }

    /**
     * @description 获取方法返回值类型
     * @param target
     * @param fieldName
     * @return
     * @return Class<?>
     */
    public static Class<?> getElementType(Class<?> target, String fieldName) {
        Class<?> elementTypeClass = null;
        try {
            Type type = target.getDeclaredField(fieldName).getGenericType();
            ParameterizedType t = (ParameterizedType) type;
            String classStr = t.getActualTypeArguments()[0].toString().replace("class ", "");
            elementTypeClass = Thread.currentThread().getContextClassLoader().loadClass(classStr);
        } catch (ClassNotFoundException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("get fieldName[" + fieldName + "] error", e);
        }
        return elementTypeClass;
    }
}
