/*
 * Copyright (C) 2012 Clarion Media, LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarionmedia.infinitum.di.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.AnimationUtils;

import com.clarionmedia.infinitum.activity.annotation.Bind;
import com.clarionmedia.infinitum.activity.annotation.InjectLayout;
import com.clarionmedia.infinitum.activity.annotation.InjectResource;
import com.clarionmedia.infinitum.activity.annotation.InjectView;
import com.clarionmedia.infinitum.context.InfinitumContext;
import com.clarionmedia.infinitum.context.exception.InfinitumConfigurationException;
import com.clarionmedia.infinitum.di.ActivityInjector;
import com.clarionmedia.infinitum.di.BeanFactory;
import com.clarionmedia.infinitum.di.annotation.Autowired;
import com.clarionmedia.infinitum.exception.InfinitumRuntimeException;
import com.clarionmedia.infinitum.reflection.ClassReflector;

/**
 * <p>
 * Implementation of {@link ActivityInjector} for injecting Android resources
 * and framework components into any object.
 * </p>
 * 
 * @author Tyler Treat
 * @version 1.0 12/18/12
 * @since 1.0
 */
public class ObjectInjector implements ActivityInjector {

	private Object mObject;
	private ClassReflector mClassReflector;
	private InfinitumContext mInfinitumContext;

	/**
	 * Creates a new {@code ObjectInjector}.
	 * 
	 * @param infinitumContext
	 *            the {@link InfinitumContext} to use
	 * @param classReflector
	 *            the {@link ClassReflector} to use
	 * @param object
	 *            the {@link Object} to use
	 */
	public ObjectInjector(InfinitumContext infinitumContext, ClassReflector classReflector, Object object) {
		mInfinitumContext = infinitumContext;
		mClassReflector = classReflector;
		mObject = object;
	}

	@Override
	public void inject() {
		List<Field> fields = mClassReflector.getAllFields(mObject.getClass());
		injectBeans(fields);
		if (Activity.class.isAssignableFrom(mObject.getClass())) {
			injectResources(fields);
			injectLayout();
			injectViews(fields);
			injectListeners(fields);
		}
	}

	private void injectBeans(List<Field> fields) {
		BeanFactory beanFactory = mInfinitumContext.getBeanFactory();
		for (Field field : fields) {
			if (!field.isAnnotationPresent(Autowired.class))
				continue;
			field.setAccessible(true);
			Autowired autowired = field.getAnnotation(Autowired.class);
			String qualifier = autowired.value().trim();
			Class<?> type = field.getType();
			Object bean = qualifier.equals("") ? beanFactory.findCandidateBean(type) : mInfinitumContext.getBean(qualifier);
			if (bean == null) {
				throw new InfinitumConfigurationException("Could not autowire property of type '" + type.getName() + "' in '"
						+ mObject.getClass().getName() + "' (no autowire candidates found)");
			}
			mClassReflector.setFieldValue(mObject, field, bean);
		}
	}

	/**
	 * Injects the {@code Activity} layout based on the {@code @InjectLayout}
	 * annotation. This takes the place of calling
	 * {@link Activity#setContentView(int)}.
	 */
	private void injectLayout() {
		InjectLayout injectLayout = mObject.getClass().getAnnotation(InjectLayout.class);
		if (injectLayout == null)
			return;
		((Activity) mObject).setContentView(injectLayout.value());
	}

	/**
	 * Injects fields annotated with {@code @InjectView}.
	 */
	private void injectViews(List<Field> fields) {
		for (Field field : fields) {
			if (!field.isAnnotationPresent(InjectView.class))
				continue;
			InjectView injectView = field.getAnnotation(InjectView.class);
			int viewId = injectView.value();
			field.setAccessible(true);
			mClassReflector.setFieldValue(mObject, field, ((Activity) mObject).findViewById(viewId));
		}
	}

	/**
	 * Injects the fields annotated with {@code @InjectResource}.
	 */
	private void injectResources(List<Field> fields) {
		for (Field field : fields) {
			if (!field.isAnnotationPresent(InjectResource.class))
				continue;
			InjectResource injectResource = field.getAnnotation(InjectResource.class);
			int resourceId = injectResource.value();
			field.setAccessible(true);
			Object resource = resolveResourceForField(field, resourceId);
			mClassReflector.setFieldValue(mObject, field, resource);
		}
	}

	/**
	 * Loads the appropriate resource based on the {@link Field} type and the
	 * given resource ID.
	 */
	private Object resolveResourceForField(Field field, int resourceId) {
		Resources resources = ((Context) mObject).getResources();
		String resourceType = resources.getResourceTypeName(resourceId);
		if (resourceType.equalsIgnoreCase("anim"))
			return AnimationUtils.loadAnimation((Context) mObject, resourceId);
		if (resourceType.equalsIgnoreCase("drawable"))
			return resources.getDrawable(resourceId);
		if (resourceType.equalsIgnoreCase("color"))
			return resources.getColor(resourceId);
		if (resourceType.equalsIgnoreCase("string"))
			return resources.getString(resourceId);
		if (resourceType.equalsIgnoreCase("integer"))
			return resources.getInteger(resourceId);
		if (resourceType.equalsIgnoreCase("bool"))
			return resources.getBoolean(resourceId);
		if (resourceType.equalsIgnoreCase("dimen"))
			return resources.getDimension(resourceId);
		if (resourceType.equalsIgnoreCase("movie"))
			return resources.getMovie(resourceId);
		if (resourceType.equalsIgnoreCase("array")) {
			if (field.getType() == int[].class || field.getType() == Integer[].class)
				return resources.getIntArray(resourceId);
			else if (field.getType() == String[].class || field.getType() == CharSequence[].class)
				return resources.getStringArray(resourceId);
			else
				return resources.obtainTypedArray(resourceId); // TODO: convert
																// to actual
																// array
		}
		if (resourceType.equalsIgnoreCase("id"))
			throw new InfinitumRuntimeException("Unable to inject field '" + field.getName() + "' in Activity '"
					+ mObject.getClass().getName() + "'. Are you injecting a view?");
		throw new InfinitumRuntimeException("Unable to inject field '" + field.getName() + "' in Activity '" + mObject.getClass().getName()
				+ "' (unsupported type).");
	}

	/**
	 * Injects event listeners into {@code View} fields annotated with
	 * {@code Bind}
	 */
	private void injectListeners(List<Field> fields) {
		for (Field field : fields) {
			if (!View.class.isAssignableFrom(field.getType()) || !field.isAnnotationPresent(Bind.class))
				continue;
			Bind bind = field.getAnnotation(Bind.class);
			Event event = bind.event();
			String callback = bind.value();
			View view = (View) mClassReflector.getFieldValue(mObject, field);
			registerCallback(view, callback, event);
		}
	}

	/**
	 * Registers an event callback.
	 */
	private void registerCallback(View view, String callback, Event event) {
		switch (event) {
		case OnClick:
			final Method onClick = mClassReflector.getMethod(mObject.getClass(), callback, View.class);
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mClassReflector.invokeMethod(mObject, onClick, v);
				}
			});
			break;
		case OnLongClick:
			final Method onLongClick = mClassReflector.getMethod(mObject.getClass(), callback, View.class);
			view.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					return (Boolean) mClassReflector.invokeMethod(mObject, onLongClick, v);
				}
			});
			break;
		case OnCreateContextMenu:
			final Method onCreateContextMenu = mClassReflector.getMethod(mObject.getClass(), callback, ContextMenu.class, View.class,
					ContextMenu.ContextMenuInfo.class);
			view.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
				@Override
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
					mClassReflector.invokeMethod(mObject, onCreateContextMenu, menu, v, menuInfo);
				}
			});
			break;
		case OnFocusChange:
			final Method onFocusChange = mClassReflector.getMethod(mObject.getClass(), callback, View.class, boolean.class);
			view.setOnFocusChangeListener(new OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					mClassReflector.invokeMethod(mObject, onFocusChange, v, hasFocus);
				}
			});
			break;
		case OnKey:
			final Method onKey = mClassReflector.getMethod(mObject.getClass(), callback, View.class, int.class, KeyEvent.class);
			view.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					return (Boolean) mClassReflector.invokeMethod(mObject, onKey, v, keyCode, event);
				}
			});
			break;
		case OnTouch:
			final Method onTouch = mClassReflector.getMethod(mObject.getClass(), callback, View.class, MotionEvent.class);
			view.setOnTouchListener(new OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					return (Boolean) mClassReflector.invokeMethod(mObject, onTouch, v, event);
				}
			});
			break;
		}
	}

}
