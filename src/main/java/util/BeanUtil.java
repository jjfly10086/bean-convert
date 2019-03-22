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
    private static final Set<Class<?>> specialClassMap = new HashSet<>(2);

    static {
        wrapClassMap.add(Boolean.class);
        wrapClassMap.add(Byte.class);
        wrapClassMap.add(Character.class);
        wrapClassMap.add(Double.class);
        wrapClassMap.add(Float.class);
        wrapClassMap.add(Integer.class);
        wrapClassMap.add(Long.class);
        wrapClassMap.add(Short.class);

        specialClassMap.add(String.class);
        specialClassMap.add(Date.class);
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
            if(sourceVal instanceof Collection){
                Collection<Object> values = (Collection) sourceVal;
                Collection<Object> rsCollection = null;
                if(sourceVal instanceof List){
                    rsCollection = new ArrayList(values.size());
                }else if(sourceVal instanceof Set){
                    rsCollection = new HashSet<>(values.size());
                }
                for(Object val : values){
                    String tempFieldName = targetMethodName.toString().replace("set", "");
                    String fieldName = tempFieldName.substring(0, 1).toLowerCase() + tempFieldName.substring(1);
                    // 获取List中元素类型
                    Class clazz = getElementType(target, fieldName);
                    // 是否为原始类型（boolean、char、byte、short、int、long、float、double）
                    if(clazz.isPrimitive()){
                        rsCollection.add(val);
                    } else if(isWrapClass(clazz)){
                        rsCollection.add(val);
                    } else if(isSpecialClass(clazz)){
                        rsCollection.add(val);
                    }else{
                        rsCollection.add(BeanUtil.convert(val, clazz));
                    }
                }
                return rsCollection;
            } else if(sourceVal instanceof Map){
                Map<Object, Object> map = (Map) sourceVal;
                Map<Object, Object> rsMap = new HashMap<>(map.size());
                for(Map.Entry<Object,Object> entry : map.entrySet()){
                    Class keyClazz = entry.getKey().getClass();
                    Object key = null;
                    if(keyClazz.isPrimitive() || isWrapClass(keyClazz) || isSpecialClass(keyClazz)){
                        key = entry.getKey();
                    }else{
                        key = BeanUtil.convert(entry.getKey(), keyClazz);
                    }
                    Class valueClazz = entry.getValue().getClass();
                    Object value = null;
                    if(valueClazz.isPrimitive() || isWrapClass(valueClazz) || isSpecialClass(valueClazz)){
                        value = entry.getValue();
                    }else{
                        value = BeanUtil.convert(entry.getValue(), valueClazz);
                    }
                    rsMap.put(key, value);
                }
                return rsMap;

            } else if(targetCls.isArray() || targetCls.isPrimitive() || isWrapClass(targetCls) || isSpecialClass(targetCls)){
                return sourceVal;

            } else if(!targetCls.isPrimitive()){
                // 引用类型
                return BeanUtil.convert(sourceVal, targetCls);
            }
            return sourceVal;
        }
    }


    private static BeanCopier getBeanCopier(Class<?> sourceCls, Class<?> targetCls){
        String beanCopierKey = generateBeanKey(sourceCls, targetCls);
        if (beanCopierMap.containsKey(beanCopierKey)) {
            return beanCopierMap.get(beanCopierKey);
        } else {
            BeanCopier beanCopier = BeanCopier.create(sourceCls, targetCls, true);
            beanCopierMap.putIfAbsent(beanCopierKey, beanCopier);
        }
        return beanCopierMap.get(beanCopierKey);

    }

    private static String generateBeanKey(Class<?> source, Class<?> target) {
        return source.getName() + "@" + target.getName();
    }

    private static boolean isWrapClass(Class<?> clazz){
        return wrapClassMap.contains(clazz);
    }

    private static boolean isSpecialClass(Class<?> clazz){
        return specialClassMap.contains(clazz);
    }

    /**
     * @description 获取方法返回值类型
     * @param target
     * @param fieldName
     * @return
     * @return Class<?>
     */
    private static Class<?> getElementType(Class<?> target, String fieldName) {
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
