package com.github.sqlhelper.util;

 
import java.lang.reflect.Field;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.log4j.Logger;  
  
public class ReflectHelper {  
	private static Logger LOGGER=Logger.getLogger(ReflectHelper.class);

    public static Object getFieldValue(Object obj , String fieldName ){  
          
        if(obj == null){  
            return null ;  
        }  
          
        Field targetField = getTargetField(obj.getClass(), fieldName);  
          
        try {  
            return FieldUtils.readField(targetField, obj, true ) ;  
        } catch (IllegalAccessException e) {  
        	LOGGER.error(e.getMessage(), e);
        }   
        return null ;  
    }  
      
    public static Field getTargetField(Class<?> targetClass, String fieldName) {  
        Field field = null;  
  
        try {  
            if (targetClass == null) {  
                return field;  
            }  
  
            if (Object.class.equals(targetClass)) {  
                return field;  
            }  
  
            field = FieldUtils.getDeclaredField(targetClass, fieldName, true);  
            if (field == null) {  
                field = getTargetField(targetClass.getSuperclass(), fieldName);  
            }  
        } catch (Exception e) {
        	LOGGER.error(e.getMessage(), e);
        }  
  
        return field;  
    }  
      
    public static void setFieldValue(Object obj , String fieldName , Object value ){  
        if(null == obj){return;}  
        Field targetField = getTargetField(obj.getClass(), fieldName);    
        try {  
             FieldUtils.writeField(targetField, obj, value) ;  
        } catch (IllegalAccessException e) {  
        	LOGGER.error(e.getMessage(), e);
        }   
    }   
}