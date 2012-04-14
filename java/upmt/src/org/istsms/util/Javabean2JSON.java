/*
Javabean2JSON: a simple approach to Java Beans serialization into JSON 
Author: Giovanni Bartolomeo
Copyright (C) 2007, 2008  University of Rome Tor Vergata - Dipartimento di Ingegneria Elettronica


This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package org.istsms.util;

//Andrea - cambiato il package. Prima era "org.json.me"
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Vector;

import org.jsonref.JSONArray;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;

public class Javabean2JSON {

	private static final Hashtable primitiveType=new Hashtable();
	static {
		primitiveType.put("java.lang.Boolean","boolean");
		primitiveType.put("java.lang.Byte","byte");
		primitiveType.put("java.lang.Character","char");
		primitiveType.put("java.lang.Double","double");
		primitiveType.put("java.lang.Float","float");
		primitiveType.put("java.lang.Integer","int");
		primitiveType.put("java.lang.Long","long");
		primitiveType.put("java.lang.Short","short");
		primitiveType.put("java.lang.String", "java.lang.String");
//		primitiveType.put("java.util.Calendar", "java.lang.String");
		primitiveType.put("boolean","boolean");
		primitiveType.put("byte","byte");
		primitiveType.put("char","char");
		primitiveType.put("double","double");
		primitiveType.put("float","float");
		primitiveType.put("int","int");
		primitiveType.put("long","long");
		primitiveType.put("short","short");		
	}
	
	private static boolean equalPrimitive(String canonicalName, String type) {	
		String cN=(String)primitiveType.get(canonicalName);
		String t=(String)primitiveType.get(type);
		if ((cN!=null)&&(t!=null)) return cN.equals(t);
		return canonicalName.equals(type);		
	}	

	private static boolean isPrimitive(String type) {
		return (primitiveType.get(type)!=null);
	}

	private static Object getPrimitive(Object value,String type) {
		Object arg=null;
		if (type.equals("boolean")||type.equals("java.lang.Boolean")) {
			
			arg=new Boolean(value.toString());
			
		} else if (type.equals("byte")||type.equals("java.lang.Byte")) {
			arg=new Byte(value.toString());
		} else if (type.equals("char")||type.equals("java.lang.Character")) {
			arg=new Character((char)value.toString().getBytes()[0]);						
		} else if (type.equals("double")||type.equals("java.lang.Double")) {
			arg=new Double(value.toString());						
		} else if (type.equals("float")||type.equals("java.lang.Float")) {
			arg=new Float(value.toString());
		} else if (type.equals("int")||type.equals("java.lang.Integer")) {
			arg=new Integer(value.toString());						
		} else if (type.equals("long")||type.equals("java.lang.Long")) {
			arg=new Long(value.toString());						
		} else if (type.equals("short")||type.equals("java.lang.Short")) {
			arg=new Short(value.toString());
		} else if (type.equals("java.lang.String")) {
			arg=value;
		} 
//		else if (type.equals("java.util.Calendar")) {
//			java.util.Calendar c=java.util.Calendar.getInstance();
//			c.setTime(new java.util.Date(Long.parseLong(value.toString())));
//			arg=c;
//		} 		
		return arg;
	}	

	public static Object fromJSONObject(JSONObject jObj) {
		return fromJSONObject(jObj,null);
	}

	public static Object fromJSONObject(JSONObject jObj0, Class suggestion) {
		
		if ((suggestion!=null)&&(suggestion.equals(JSONObject.class))) return jObj0;	
		JSONObject jObj=(JSONObject)jObj0;
		Class c;
		String cl=null;
		if (suggestion!=null) cl=suggestion.getCanonicalName();
		try {		
			try {
				cl=jObj.getString("__class");						
			} catch (JSONException e) {										
			} catch (ClassCastException e) {											
			}					
			if (cl==null) return null;
			if (isPrimitive(cl)) {
				return getPrimitive(jObj0.getString("__value"),jObj0.getString("__class"));
			}
			if (isCalendar(cl)) {
				return toJavaCalendar(jObj0);
			}
			c = Class.forName(cl);
			Object temp=c.newInstance();
			Method[] m=c.getMethods();
			//Note: private and protected methods are not considered!
			Method method;
			String methodName;
			for (int i=0;i<m.length;i++) {
				method=m[i];			
				if (Modifier.isStatic(method.getModifiers())) continue;				
				methodName=method.getName();
				if (methodName.startsWith("set")) {	
					Class[] params=method.getParameterTypes();
					if (params.length==1) {
						//String key=methodName.substring(3); //ANDREA - cambiato in:
						String key=methodName.substring(3, 4).toLowerCase()+methodName.substring(4);
						String type=params[0].getCanonicalName();							
						if (params[0].isArray()) {							
							//it's an array!
							//type=type.substring(0, type.length()-2); //skip '[]' now done inside _fromJSONArray()
							JSONArray jArr=null;
							try {
								jArr=jObj.getJSONArray(key);
							} catch (JSONException e) {}
							Object objArr=fromJSONArray(jArr,params[0]); //10.7.08 GB to support array of array deserialization
							if (objArr!=null) method.invoke(temp, objArr);							
						} else {
							Object value=null;
							try {
								value=jObj.get(key);
							} catch (JSONException e) {								
							}
							//if (value==null) continue; //ANDREA - cambiato in:
							if (value==null || value.equals(null)) continue;
							Object arg=null;
							if (isCalendar(type)) try {
								arg=toJavaCalendar((JSONObject)value);
							} catch (ClassCastException e) {
								e.printStackTrace();
							}
							else {
								if (value instanceof JSONObject) { 
									arg=fromJSONObject((JSONObject)value,params[0]);
								} else 
									arg=getPrimitive(value,type);					
							}
							method.invoke(temp, new Object[]{arg});
						}					
					}
				}
			}		
			return temp;							
		} catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());		
		} 
		return null;
	}				

	public static Object fromJSONArray(JSONArray jArr, Class arrayType) {		
		if (jArr!=null&&arrayType!=null) {
			Class baseType=arrayType.getComponentType();			
			if (baseType==null) return null; //it was not an array!		
			String baseTypeName=baseType.getCanonicalName();
			int length=jArr.length();							
			Object argArray = Array.newInstance(baseType, length);
			try {
				Vector v=new Vector();
				for (int j=0;j<jArr.length();j++) {
					//four cases: the json Array element may be:
					//a. a JSON Array (in case of array of arrays)
					//b. a calendar
					//c. a JSON Object
					//d. a primitive value (serialized as a String)									
					Object jElement=jArr.get(j);	
					if (jElement.equals(null)) continue;
					if (jElement instanceof JSONArray) {						
						Object element=fromJSONArray((JSONArray)jElement,baseType);						
						if (element!=null) Array.set(argArray, j, element);
					} else if (isCalendar(baseTypeName)) {
						Object element=toJavaCalendar((JSONObject)jElement);
						if (element!=null) Array.set(argArray, j, element);
					} else if (jElement instanceof JSONObject) {
						Object element=fromJSONObject((JSONObject)jElement,baseType);
						if (element!=null) Array.set(argArray, j, element);
					} else {
						Object primitive=getPrimitive(jArr.getString(j),baseTypeName);
						if (primitive!=null) Array.set(argArray, j, primitive);
					} 
				}																

				return argArray;				
			} catch (NegativeArraySizeException e) {				
			} catch (JSONException e) {				
			}											
		}		
		return null;
	}

	public static JSONObject toJSONObject(Object obj) {				
		JSONObject temp=_toJSONObject(obj);
		if (!(obj instanceof java.util.Calendar)) try {
			temp.put("__class", obj.getClass().getCanonicalName());
		} catch (JSONException e) {		
			e.printStackTrace();
			return null;
		}
		return temp;
	}

	private static JSONObject _toJSONObject(Object obj) {
		if (obj instanceof JSONObject) return (JSONObject) obj;
		if (obj instanceof java.util.Calendar) return fromJavaCalendar((java.util.Calendar)obj);
		JSONObject temp=new JSONObject();
		if (isPrimitive(obj.getClass().getCanonicalName())) {			
			try {		
					temp.put("__value", 
							obj.toString());
//							(obj instanceof java.util.Calendar)
//							?""+((java.util.Calendar)obj).getTime().getTime()	
//							:obj.toString());
			} catch (JSONException e) {				
				e.printStackTrace();
			}
		} else {
			addInherited(obj,obj.getClass(),temp);
		}
		return temp;			
	}

	public static JSONArray toJSONArray(Object value) {
		return _toJSONArray(value,value.getClass());
	}
	
	private static JSONArray _toJSONArray(Object value, Class c) {
		if (value==null) return null;
		if (!c.isArray()) return null;
		if (value.getClass().isArray()) {						
			//it's an array!						
			Vector v=new Vector();
			int length=Array.getLength(value);					
			for (int j=0;j<length;j++) {
				Object element=Array.get(value, j);
				if (element!=null) {
					if (element.getClass().isArray()) {
						v.add(_toJSONArray(element,c.getComponentType()));
					} else {
						String suggestedComponentType=c.getComponentType().getCanonicalName();		
						if (isCalendar(element.getClass().getCanonicalName())) {
							v.add(fromJavaCalendar((java.util.Calendar)element));
						} else if (isPrimitive(element.getClass().getCanonicalName())) {
							v.add(equalPrimitive(element.getClass().getCanonicalName(),suggestedComponentType)
								  ?  
								     element.toString()										
//									 (element instanceof java.util.Calendar)
//							   		 ?""+((java.util.Calendar)element).getTime().getTime()	
//									 :element.toString()
										  
								  :toJSONObject(element));
						} else {											 
							v.add(suggestedComponentType.equals(element.getClass().getCanonicalName())?
									_toJSONObject(element):
										toJSONObject(element));
						}
					}
				} else {
					v.add(JSONObject.NULL);
				}
			}
			return new JSONArray(v);
		}
		return null;
	}

	private static void addInherited(Object obj, Class cl, JSONObject jObj) {		
		if (cl==Object.class) return;		
		addAttributes(obj, cl, jObj);
		Class ancestor = cl.getSuperclass();  	     	
		addInherited(obj, ancestor, jObj);      		        	    
	}	

	private static void addAttributes(Object obj, Class c, JSONObject jObj) {		
		//String objectClass=c.getCanonicalName();
//		Method[] m=c.getDeclaredMethods();
		Method[] m=c.getMethods();
		//Note: private and protected methods are not considered!
		Method method;
		String methodName;
		for (int i=0;i<m.length;i++) {
			method=m[i];			
			if (Modifier.isStatic(method.getModifiers())) continue;
			if (!method.getDeclaringClass().equals(c)) continue; //it is an inherited method, do not consider
			//TODO:maybe also private and protected methods have to be discarder!
			methodName=method.getName();
			if (methodName.startsWith("get")||methodName.startsWith("is")) {
				Class[] params=method.getParameterTypes();
				if (params.length==0) {
					String key;
					if (methodName.startsWith("get")) key=methodName.substring(3);
					else key=methodName.substring(2);
					String type=method.getReturnType().getCanonicalName();
					log("found method GET "+key+" returned type: "+type);							
					Object value;
					try {
						value = method.invoke(obj, (Object[])null);					
						//now enumerate primitive types, if it is a primitive value, put it in the json object
						//its value as a string; otherwise call recursively this method
						if (value==null) continue;
						if (value.getClass().isArray()) {
							//it's an array!		
							JSONArray ja=_toJSONArray(value,method.getReturnType()); //10.07.08 GB to support array of array serialization
							if (ja!=null) jObj.put(key, ja); 
						} else {
							put(jObj,type,key,value);
						}

					} catch (Exception e) {
						log ("Exception: "+e.getMessage());					
					}				
				}
			}
		}				
	}

	private static void put(JSONObject jObj, String type, String key, Object value) {
		try {
			if (isCalendar(type)) {
				jObj.put(key,fromJavaCalendar((java.util.Calendar)value));				
			} else if (isPrimitive(type)) {
				jObj.put(key, 
						value.toString());					
			} else {
				if (isPrimitive(value.getClass().getCanonicalName())) {
					if (!equalPrimitive(value.getClass().getCanonicalName(),type)) {
						//in case the getXXX method returns a generic interface, then put an explicit information
						//about the actual object's class
						JSONObject jo=_toJSONObject(value);
						jo.put("__class", value.getClass().getCanonicalName());
						jObj.put(key, jo);
					} else {
						jObj.put(key, value.toString());
					}
				} else {
					JSONObject jo;
					if (value.getClass().getCanonicalName().equals(type)) {
						jo=_toJSONObject(value);
					} else {
						jo=toJSONObject(value);
					}
					jObj.put(key, jo);
				}				
			}
		} catch (JSONException e) {
			log(e.getMessage());
		}
	}

	private static void log(String s) {
		//System.out.println("[Javabean2JSON] "+s);
	}		
	
	//extra methods to manage java.util.Calendar serialization
	

	private static Object toJavaCalendar(JSONObject jo) {
		if (jo==null) return null;
		try {
			if (jo.getString("__class").equals(Calendar.class.getCanonicalName())) {
				java.util.Calendar c=java.util.Calendar.getInstance();
				c.setTime(new java.util.Date(Long.parseLong(jo.getString("__value"))));
				c.setTimeZone(java.util.TimeZone.getTimeZone(jo.getString("__timezn")));
			return c;	
			}								
		} catch (JSONException e) {	
			e.printStackTrace();		
		} catch (RuntimeException r) {
			r.printStackTrace();
		}
		return null;
	}

	private static JSONObject fromJavaCalendar(Calendar obj) {	
		if (obj==null) return null;
		try {
			JSONObject jo=new JSONObject();
			jo.put("__class", Calendar.class.getCanonicalName());
			jo.put("__value", ""+obj.getTime().getTime());
			jo.put("__timezn", obj.getTimeZone().getID());	
			return jo;
		} catch (JSONException e) {		
			e.printStackTrace();
			return null;
		}		
	}

	private static boolean isCalendar(String cl) {		
		return cl.equals(Calendar.class.getCanonicalName());
	}
}
