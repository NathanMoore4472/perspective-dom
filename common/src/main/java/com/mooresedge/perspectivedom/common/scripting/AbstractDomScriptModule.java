package com.mooresedge.perspectivedom.common.scripting;

import java.util.List;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.script.hints.JythonElement;
import com.inductiveautomation.ignition.common.script.hints.ScriptArg;

/**
 * The system.dom scripting functions.
 *
 * The public methods here define what scripts can call, and their docs (shown
 * in Designer autocomplete) live in AbstractDomScriptModule.properties. Each
 * scope supplies its own implementation: the Gateway does the real work, since
 * that is where Perspective scripts run, while the Designer only needs the
 * signatures for script hints.
 */
public abstract class AbstractDomScriptModule {

	static {
		BundleUtil.get().addBundle(
				AbstractDomScriptModule.class.getSimpleName(),
				AbstractDomScriptModule.class.getClassLoader(),
				AbstractDomScriptModule.class.getName().replace('.', '/'));
	}

	// ==================== By ID ====================

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object getElementById(@ScriptArg("id") String id) {
		return getElementByIdImpl(id);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object getPageElementById(@ScriptArg("id") String id) {
		return getPageElementByIdImpl(id);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> getElementsById(@ScriptArg("id") String id) {
		return getElementsByIdImpl(id);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> getPageElementsById(@ScriptArg("id") String id) {
		return getPageElementsByIdImpl(id);
	}

	// ==================== By Name ====================

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object getElementByName(@ScriptArg("name") String name) {
		return getElementByNameImpl(name);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object getPageElementByName(@ScriptArg("name") String name) {
		return getPageElementByNameImpl(name);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> getElementsByName(@ScriptArg("name") String name) {
		return getElementsByNameImpl(name);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> getPageElementsByName(@ScriptArg("name") String name) {
		return getPageElementsByNameImpl(name);
	}

	// ==================== Query Selectors ====================

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object querySelector(@ScriptArg("selector") String selector) {
		return querySelectorImpl(selector);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> querySelectorAll(@ScriptArg("selector") String selector) {
		return querySelectorAllImpl(selector);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public Object getPageQuerySelector(@ScriptArg("selector") String selector) {
		return getPageQuerySelectorImpl(selector);
	}

	@JythonElement(docBundlePrefix = "AbstractDomScriptModule")
	public List<Object> getPageQuerySelectorAll(@ScriptArg("selector") String selector) {
		return getPageQuerySelectorAllImpl(selector);
	}

	// ==================== Abstract Implementations ====================

	protected abstract Object getElementByIdImpl(String id);

	protected abstract Object getPageElementByIdImpl(String id);

	protected abstract List<Object> getElementsByIdImpl(String id);

	protected abstract List<Object> getPageElementsByIdImpl(String id);

	protected abstract Object getElementByNameImpl(String name);

	protected abstract Object getPageElementByNameImpl(String name);

	protected abstract List<Object> getElementsByNameImpl(String name);

	protected abstract List<Object> getPageElementsByNameImpl(String name);

	protected abstract Object querySelectorImpl(String selector);

	protected abstract List<Object> querySelectorAllImpl(String selector);

	protected abstract Object getPageQuerySelectorImpl(String selector);

	protected abstract List<Object> getPageQuerySelectorAllImpl(String selector);
}
