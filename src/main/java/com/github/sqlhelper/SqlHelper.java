package com.github.sqlhelper;

import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.log4j.Logger;

import com.github.sqlhelper.util.ReflectHelper;

/**
 * Sql执行时间记录拦截器 
 * @author suzy2
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
	@Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
	@Signature(type = StatementHandler.class, method = "batch", args = { Statement.class })})
public class SqlHelper implements Interceptor {
	
	private String noPrint;
	
	private static Logger LOGGER=Logger.getLogger(SqlHelper.class);
	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		Object target = invocation.getTarget();

		long startTime = System.currentTimeMillis();
		StatementHandler statementHandler = (StatementHandler)target;
		try {
			return invocation.proceed();
		} finally {
			if(LOGGER.isDebugEnabled()){
				long endTime = System.currentTimeMillis();
				long sqlCost = endTime - startTime;
				// 格式化Sql语句，去除换行符，替换参数
				String sql = formatSql( statementHandler);
				if(StringUtils.isNotBlank(sql)){
					sql =beautifySql(sql);
					LOGGER.debug("SQL：[" + sql + "]执行耗时[" + sqlCost + "ms]");
				}

			}

		}
	}

	private String formatSql(StatementHandler statementHandler) {

		//     	  Configuration configuration = session.getConfiguration();

		/*Configuration  configuration = null;
           TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();*/
		BoundSql boundSql = statementHandler.getBoundSql();
		Object params =boundSql.getParameterObject();

	
		if(StringUtils.isNotBlank(noPrint)){
			Object obj =statementHandler;
			if(statementHandler instanceof RoutingStatementHandler){
				obj =  ReflectHelper.getFieldValue(statementHandler, "delegate");    
			}
			MappedStatement mappedStatement = (MappedStatement)ReflectHelper.getFieldValue(obj, "mappedStatement");    
			Matcher m = p.matcher(mappedStatement.getId());
			if( m.matches()) {
				return null;
			}
		}
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		String sql = boundSql.getSql();
		try {
			if (parameterMappings != null) {
				for (int i = 0; i < parameterMappings.size(); i++) {
					ParameterMapping parameterMapping = parameterMappings.get(i);
					if (parameterMapping.getMode() != ParameterMode.OUT) {
						Object value;
						String propertyName = parameterMapping.getProperty();
						JdbcType jdbcType = parameterMapping.getJdbcType();
						if (boundSql.hasAdditionalParameter(propertyName)) {
							value = boundSql.getAdditionalParameter(propertyName);
						} else if (params == null) {
							value = null;
						} else if (jdbcType == null) {
							value = params;
							if(params instanceof Map){
								value =((Map<?, ?>) params).get(propertyName);
							}
						}
						/* else if (typeHandlerRegistry.hasTypeHandler(params.getClass())) {
			                   value = params;
			               }*/
						else if(isWrapClass(params.getClass())) {
							value =params;
						}else if( params instanceof String) {
							value =params;
						}else if( params instanceof Arrays) {
							value =params;
						} else {
							MetaObject metaObject = SystemMetaObject.forObject(params);
							value = metaObject.getValue(propertyName);
						}

						if (value == null && jdbcType == null) jdbcType =JdbcType.NULL;
						sql = replaceParameter(sql, value, jdbcType, parameterMapping.getJavaType());
					}
				}
			}
		} catch (Exception e) {
			LOGGER.info("sqlhelper error:"+sql);
			LOGGER.info(e.getMessage(),e);
		}
		return sql;
	}
	/**
	 * 根据类型替换参数
	 * 仅作为数字和字符串两种类型进行处理，需要特殊处理的可以继续完善这里
	 *
	 * @param sql
	 * @param value
	 * @param jdbcType
	 * @param javaType
	 * @return
	 */
	private static String replaceParameter(String sql, Object value, JdbcType jdbcType, Class<?> javaType) {
		String strValue = String.valueOf(value);
		if (jdbcType != null) {
			switch (jdbcType) {
			//数字
			case BIT:
			case TINYINT:
			case SMALLINT:
			case INTEGER:
			case BIGINT:
			case FLOAT:
			case REAL:
			case DOUBLE:
			case NUMERIC:
			case DECIMAL:
				break;
				//日期
			case DATE:
			case TIME:
			case TIMESTAMP:
				//其他，包含字符串和其他特殊类型
			default:
				strValue = "'" + strValue + "'";


			}
		} else if (Number.class.isAssignableFrom(javaType)) {
			//不加单引号
		} else {
			strValue = "'" + strValue + "'";
		}
		return sql.replaceFirst("\\?", strValue);
	}
	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {

	}
	private static ConcurrentHashMap<Class<?>, Boolean> booleanMap= new ConcurrentHashMap<Class<?>, Boolean>();
	private   boolean isWrapClass(Class<?> clz) {   
		Boolean cache = booleanMap.get(clz);
		
		if(null != cache) {
			return cache;
		}
		if( clz.isPrimitive()) {
			booleanMap.put(clz, true);
			return true;
		}
		try {     
			booleanMap.put(clz, true);
			return ((Class<?>) clz.getField("TYPE").get(null)).isPrimitive();    
		} catch (Exception e) { 
			booleanMap.put(clz, false);
			return false;     
		}     
	}     

	/**
	 * 美化Sql
	 */
	private String beautifySql(String sql) {
		// sql = sql.replace("\n", "").replace("\t", "").replace("  ", " ").replace("( ", "(").replace(" )", ")").replace(" ,", ",");
		sql = sql.replaceAll("[\\s\n ]+"," ");
		return sql;
	}
    
	Pattern p =null;
 	public String getNoPrint() {
		return noPrint;
	}

	public void setNoPrint(String noPrint) {
		if(noPrint!=null) {
			p = Pattern.compile(noPrint);
		}
		this.noPrint = noPrint;
	}



}