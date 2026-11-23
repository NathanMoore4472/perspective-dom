package com.mooresedge.perspectivedom.gateway.scripting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import com.inductiveautomation.ignition.common.gson.JsonPrimitive;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.perspective.common.api.PropertyType;
import com.inductiveautomation.perspective.gateway.api.Component;
import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import com.inductiveautomation.perspective.gateway.model.PageModel;
import com.inductiveautomation.perspective.gateway.model.ViewModel;
import com.inductiveautomation.perspective.gateway.property.PropertyTree;
import com.inductiveautomation.perspective.gateway.script.ComponentModelScriptWrapper;
import org.python.core.PyList;

import com.mooresedge.perspectivedom.common.scripting.AbstractDomScriptModule;

/**
 * Gateway implementation of the system.dom functions.
 *
 * Perspective attaches the current page and view to the thread before running
 * any session script (the same mechanism the built-in system.perspective
 * functions use), so we can find the page without the caller passing 'self'.
 */
public class GatewayDomScriptModule extends AbstractDomScriptModule {

	private static final String DOM_ID_KEY = "domId";
	private static final String STYLE_CLASSES_KEY = "style.classes";

	// ==================== By ID ====================

	@Override
	protected Object getElementByIdImpl(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		ViewModel currentView = requireCurrentView("getElementById");
		return findFirst(List.of(currentView), comp -> id.equals(readDomId(comp)));
	}

	@Override
	protected Object getPageElementByIdImpl(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		List<ViewModel> views = getPageViews("getPageElementById");
		return findFirst(views, comp -> id.equals(readDomId(comp)));
	}

	@Override
	protected List<Object> getElementsByIdImpl(String id) {
		if (id == null || id.isEmpty()) {
			return newResultList();
		}
		ViewModel currentView = requireCurrentView("getElementsById");
		return findAll(List.of(currentView), comp -> id.equals(readDomId(comp)));
	}

	@Override
	protected List<Object> getPageElementsByIdImpl(String id) {
		if (id == null || id.isEmpty()) {
			return newResultList();
		}
		List<ViewModel> views = getPageViews("getPageElementsById");
		return findAll(views, comp -> id.equals(readDomId(comp)));
	}

	// ==================== By Name ====================

	@Override
	protected Object getElementByNameImpl(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		ViewModel currentView = requireCurrentView("getElementByName");
		return findFirst(List.of(currentView), comp -> name.equals(comp.getName()));
	}

	@Override
	protected Object getPageElementByNameImpl(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		List<ViewModel> views = getPageViews("getPageElementByName");
		return findFirst(views, comp -> name.equals(comp.getName()));
	}

	@Override
	protected List<Object> getElementsByNameImpl(String name) {
		if (name == null || name.isEmpty()) {
			return newResultList();
		}
		ViewModel currentView = requireCurrentView("getElementsByName");
		return findAll(List.of(currentView), comp -> name.equals(comp.getName()));
	}

	@Override
	protected List<Object> getPageElementsByNameImpl(String name) {
		if (name == null || name.isEmpty()) {
			return newResultList();
		}
		List<ViewModel> views = getPageViews("getPageElementsByName");
		return findAll(views, comp -> name.equals(comp.getName()));
	}

	// ==================== Query Selectors ====================

	@Override
	protected Object querySelectorImpl(String selector) {
		if (selector == null || selector.trim().isEmpty()) {
			return null;
		}
		ViewModel currentView = requireCurrentView("querySelector");
		Predicate<ComponentModel> predicate = SelectorMatcher.compile(selector, GatewayDomScriptModule::readDomId,
				GatewayDomScriptModule::readStyleClasses);
		return findFirst(List.of(currentView), predicate);
	}

	@Override
	protected List<Object> querySelectorAllImpl(String selector) {
		if (selector == null || selector.trim().isEmpty()) {
			return newResultList();
		}
		ViewModel currentView = requireCurrentView("querySelectorAll");
		Predicate<ComponentModel> predicate = SelectorMatcher.compile(selector, GatewayDomScriptModule::readDomId,
				GatewayDomScriptModule::readStyleClasses);
		return findAll(List.of(currentView), predicate);
	}

	@Override
	protected Object getPageQuerySelectorImpl(String selector) {
		if (selector == null || selector.trim().isEmpty()) {
			return null;
		}
		List<ViewModel> views = getPageViews("getPageQuerySelector");
		Predicate<ComponentModel> predicate = SelectorMatcher.compile(selector, GatewayDomScriptModule::readDomId,
				GatewayDomScriptModule::readStyleClasses);
		return findFirst(views, predicate);
	}

	@Override
	protected List<Object> getPageQuerySelectorAllImpl(String selector) {
		if (selector == null || selector.trim().isEmpty()) {
			return newResultList();
		}
		List<ViewModel> views = getPageViews("getPageQuerySelectorAll");
		Predicate<ComponentModel> predicate = SelectorMatcher.compile(selector, GatewayDomScriptModule::readDomId,
				GatewayDomScriptModule::readStyleClasses);
		return findAll(views, predicate);
	}

	// ==================== Helpers ====================

	private static ViewModel requireCurrentView(String functionName) {
		ViewModel currentView = ViewModel.VIEW.get();
		if (currentView == null) {
			throw new IllegalStateException(
					"system.dom." + functionName + " can only be called from a Perspective script running in a view "
							+ "(component events, property change scripts, message handlers, etc).");
		}
		return currentView;
	}

	private static List<ViewModel> getPageViews(String functionName) {
		ViewModel currentView = ViewModel.VIEW.get();
		PageModel page = PageModel.PAGE.get();
		if (page == null && currentView != null && currentView.getPage() instanceof PageModel) {
			page = (PageModel) currentView.getPage();
		}
		if (page == null && currentView == null) {
			throw new IllegalStateException(
					"system.dom." + functionName + " can only be called from a Perspective script running on a page "
							+ "(component events, property change scripts, message handlers, etc).");
		}

		List<ViewModel> views = new ArrayList<>();
		if (currentView != null) {
			views.add(currentView);
		}
		if (page != null) {
			for (ViewModel view : page.getViews()) {
				if (view != currentView) {
					views.add(view);
				}
			}
		}
		return views;
	}

	/**
	 * Searches the given views in order and returns the first matching component,
	 * wrapped for scripting, or null if none match.
	 */
	private static Object findFirst(List<ViewModel> views, Predicate<ComponentModel> predicate) {
		for (ViewModel view : views) {
			if (!view.isRunning()) {
				continue;
			}
			Optional<ComponentModel> match = findFirstInView(view, predicate);
			if (match.isPresent()) {
				return new ComponentModelScriptWrapper(match.get());
			}
		}
		return null;
	}

	/**
	 * Searches the given views in order and returns all matching components,
	 * wrapped for scripting, or an empty list if none match.
	 */
	private static List<Object> findAll(List<ViewModel> views, Predicate<ComponentModel> predicate) {
		PyList results = new PyList();
		for (ViewModel view : views) {
			if (!view.isRunning()) {
				continue;
			}
			findAllInView(view, predicate, results);
		}
		return asList(results);
	}

	/**
	 * Multi-match results are real Jython lists, so scripts get a genuine Python
	 * list (isinstance(x, list) is True) rather than a wrapped java.util.ArrayList.
	 */
	private static List<Object> newResultList() {
		return asList(new PyList());
	}

	@SuppressWarnings("unchecked")
	private static List<Object> asList(PyList list) {
		return (List<Object>) list;
	}

	/**
	 * Depth-first search of a view's component tree in document order for the first match.
	 */
	private static Optional<ComponentModel> findFirstInView(ViewModel view, Predicate<ComponentModel> predicate) {
		ComponentModel root = view.getRootContainer();
		if (root == null) {
			return Optional.empty();
		}

		Deque<ComponentModel> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty()) {
			ComponentModel component = stack.pop();
			if (predicate.test(component)) {
				return Optional.of(component);
			}

			// Push children in reverse so they are visited in their natural order
			List<ComponentModel> children = new ArrayList<>();
			for (Component child : component.getChildren()) {
				if (child instanceof ComponentModel) {
					children.add((ComponentModel) child);
				}
			}
			for (int i = children.size() - 1; i >= 0; i--) {
				stack.push(children.get(i));
			}
		}
		return Optional.empty();
	}

	/**
	 * Depth-first search of a view's component tree in document order collecting all matches.
	 */
	private static void findAllInView(ViewModel view, Predicate<ComponentModel> predicate, PyList results) {
		ComponentModel root = view.getRootContainer();
		if (root == null) {
			return;
		}

		Deque<ComponentModel> stack = new ArrayDeque<>();
		stack.push(root);
		while (!stack.isEmpty()) {
			ComponentModel component = stack.pop();
			if (predicate.test(component)) {
				results.append(new ComponentModelScriptWrapper(component));
			}

			List<ComponentModel> children = new ArrayList<>();
			for (Component child : component.getChildren()) {
				if (child instanceof ComponentModel) {
					children.add((ComponentModel) child);
				}
			}
			for (int i = children.size() - 1; i >= 0; i--) {
				stack.push(children.get(i));
			}
		}
	}

	static String readDomId(ComponentModel component) {
		return readString(component, PropertyType.meta, DOM_ID_KEY, "meta.domId");
	}

	static String readStyleClasses(ComponentModel component) {
		return readString(component, PropertyType.props, STYLE_CLASSES_KEY, "props.style.classes");
	}

	/**
	 * Reads a property as a string, or null if the component doesn't have it.
	 *
	 * Property trees may only be read on their own execution queue. runOrSubmit
	 * runs inline if we're already on it, and otherwise waits for it, which is
	 * the same approach Perspective's own script wrappers take.
	 */
	private static String readString(ComponentModel component, PropertyType type, String path, String label) {
		PropertyTree tree = component.getPropertyTreeOf(type);
		if (tree == null) {
			return null;
		}

		Object value;
		try {
			value = tree.getQueue()
					.runOrSubmit(() -> tree.read(path).map(QualifiedValue::getValue).orElse(null))
					.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while reading " + label, e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("Unable to read " + label + " of " + component.getQualifiedPath(),
					e.getCause());
		}

		if (value instanceof JsonPrimitive) {
			return ((JsonPrimitive) value).getAsString();
		}
		return value == null ? null : value.toString();
	}
}
