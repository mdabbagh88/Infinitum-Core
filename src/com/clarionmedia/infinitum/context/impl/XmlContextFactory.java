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

package com.clarionmedia.infinitum.context.impl;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Scanner;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.clarionmedia.infinitum.context.ContextFactory;
import com.clarionmedia.infinitum.context.InfinitumContext;
import com.clarionmedia.infinitum.context.exception.InfinitumConfigurationException;
import com.clarionmedia.infinitum.internal.ModuleUtils;
import com.clarionmedia.infinitum.internal.ModuleUtils.Module;

/**
 * <p>
 * Provides access to an {@link InfinitumContext} singleton. In order for this
 * class to function properly, an {@code infinitum.cfg.xml} file must be created
 * and {@link ContextFactory#configure(Context, int)} must be called before
 * accessing the {@code InfinitumContext} or an
 * {@link InfinitumConfigurationException} will be thrown.
 * {@link XmlContextFactory} singletons should be acquired by calling the static
 * method {@link XmlContextFactory#instance()}.
 * </p>
 * <p>
 * {@code XmlContextFactory} uses the Simple XML framework to read
 * {@code infinitum.cfg.xml} and create the {@code InfinitumContext}.
 * </p>
 * 
 * @author Tyler Treat
 * @version 1.0 06/25/12
 * @since 1.0
 */
public class XmlContextFactory extends ContextFactory {

	private static XmlApplicationContext sInfinitumContext;

	@Override
	public InfinitumContext configure(Context context) throws InfinitumConfigurationException {
		if (sInfinitumContext != null)
			return sInfinitumContext;
		mContext = context.getApplicationContext();
		Resources res = mContext.getResources();
		int id = res.getIdentifier("infinitum", "raw", mContext.getPackageName());
		if (id == 0)
			throw new InfinitumConfigurationException("Configuration infinitum.cfg.xml could not be found.");
		sInfinitumContext = configureFromXml(id);
		return sInfinitumContext;
	}

	@Override
	public InfinitumContext configure(Context context, int configId) throws InfinitumConfigurationException {
		if (sInfinitumContext != null)
			return sInfinitumContext;
		mContext = context.getApplicationContext();
		sInfinitumContext = configureFromXml(configId);
		return sInfinitumContext;
	}

	@Override
	public InfinitumContext getContext() throws InfinitumConfigurationException {
		if (sInfinitumContext == null)
			throw new InfinitumConfigurationException("Infinitum context not configured!");
		return sInfinitumContext;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends InfinitumContext> T getContext(Class<T> contextType) throws InfinitumConfigurationException {
		if (sInfinitumContext == null)
			throw new InfinitumConfigurationException("Infinitum context not configured!");
		if (contextType == XmlApplicationContext.class)
			return (T) sInfinitumContext;
		for (InfinitumContext context : sInfinitumContext.getChildContexts()) {
			if (contextType.isAssignableFrom(context.getClass()))
				return (T) context;
		}
		throw new InfinitumConfigurationException("Configuration of type '" + contextType.getClass().getName() + "' could not be found.");
	}

	private XmlApplicationContext configureFromXml(int configId) {
		long start = Calendar.getInstance().getTimeInMillis();
		Resources resources = mContext.getResources();
		Strategy strategy = new TreeStrategy("clazz", "len");
		Serializer serializer = new Persister(strategy);
		try {
			InputStream stream = resources.openRawResource(configId);
			String xml = new Scanner(stream).useDelimiter("\\A").next();
			XmlApplicationContext ret = serializer.read(XmlApplicationContext.class, xml);
			if (ret == null)
				throw new InfinitumConfigurationException("Unable to initialize Infinitum configuration.");
			addChildContexts(ret);
			ret.postProcess(mContext);
			return ret;
		} catch (Exception e) {
			throw new InfinitumConfigurationException("Unable to initialize Infinitum configuration.", e);
		} finally {
			long stop = Calendar.getInstance().getTimeInMillis();
			Log.i(getClass().getSimpleName(), "Context initialized in " + (stop - start) + " milliseconds");
		}
	}

	/**
	 * This loads all of the non-core module contexts as children of the root
	 * context.
	 */
	private void addChildContexts(XmlApplicationContext parent) {
		for (Module module : Module.values()) {
			if (ModuleUtils.hasModule(module))
				parent.addChildContext(module.initialize(parent));
		}
	}

}
