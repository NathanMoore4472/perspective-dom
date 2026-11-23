package com.mooresedge.perspectivedom.designer.scripting;

import java.util.List;

import com.mooresedge.perspectivedom.common.scripting.AbstractDomScriptModule;

/**
 * Designer registration of the system.dom functions.
 *
 * Perspective scripts always run on the Gateway, even when previewing in the
 * Designer, so this exists only to give the script editors autocomplete and
 * docs. Calling it from Designer-scoped scripts (e.g. the Script Console)
 * raises a clear error instead of failing silently.
 */
public class DesignerDomScriptModule extends AbstractDomScriptModule {

	@Override
	protected Object getElementByIdImpl(String id) {
		throw notAvailable("getElementById");
	}

	@Override
	protected Object getPageElementByIdImpl(String id) {
		throw notAvailable("getPageElementById");
	}

	@Override
	protected List<Object> getElementsByIdImpl(String id) {
		throw notAvailable("getElementsById");
	}

	@Override
	protected List<Object> getPageElementsByIdImpl(String id) {
		throw notAvailable("getPageElementsById");
	}

	@Override
	protected Object getElementByNameImpl(String name) {
		throw notAvailable("getElementByName");
	}

	@Override
	protected Object getPageElementByNameImpl(String name) {
		throw notAvailable("getPageElementByName");
	}

	@Override
	protected List<Object> getElementsByNameImpl(String name) {
		throw notAvailable("getElementsByName");
	}

	@Override
	protected List<Object> getPageElementsByNameImpl(String name) {
		throw notAvailable("getPageElementsByName");
	}

	@Override
	protected Object querySelectorImpl(String selector) {
		throw notAvailable("querySelector");
	}

	@Override
	protected List<Object> querySelectorAllImpl(String selector) {
		throw notAvailable("querySelectorAll");
	}

	@Override
	protected Object getPageQuerySelectorImpl(String selector) {
		throw notAvailable("getPageQuerySelector");
	}

	@Override
	protected List<Object> getPageQuerySelectorAllImpl(String selector) {
		throw notAvailable("getPageQuerySelectorAll");
	}

	private static IllegalStateException notAvailable(String function) {
		return new IllegalStateException(
				"system.dom." + function + " only works in Perspective scripts, which run on the Gateway. "
						+ "It cannot be used from the Designer Script Console.");
	}
}
