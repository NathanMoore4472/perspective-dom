package com.mooresedge.perspectivedom.gateway.scripting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.inductiveautomation.perspective.gateway.model.ComponentModel;

/**
 * Parses and evaluates CSS-like selectors against Perspective ComponentModel instances.
 *
 * Supported selector syntax:
 *   - Universal: '*'
 *   - Type: short 'button', 'label', 'flex' (matches the last segment of the type), or a full
 *     three-part type such as 'ia.input.button'
 *   - ID: '#statusLabel' (matches meta.domId)
 *   - Classes: '.warning', '.warning.big' (every class must be in props.style.classes)
 *   - Attributes: '[name="Save"]' (meta.name), '[domId="foo"]', '[type="ia.input.button"]'
 *   - Compound: 'button#submitBtn', 'label.error', 'ia.display.label.warn', 'button[name="Save"]'
 *   - Comma-separated: 'button, label, #myId' (matches any)
 *
 * Anything else (combinators, pseudo-classes, other attributes or operators) raises an
 * IllegalArgumentException rather than being ignored, so a typo can never widen a match.
 *
 * A leading dotted word is read as a full type when it has three or more parts
 * ('ia.display.label.warn' is type 'ia.display.label' plus class 'warn'), and otherwise as a
 * short type followed by classes ('label.warn').
 */
public class SelectorMatcher {

	private static final int FULL_TYPE_SEGMENTS = 3;

	public static class Clause {
		String tag;
		String fullType;
		String domId;
		String name;
		String attrDomId;
		String attrType;
		final List<String> classNames = new ArrayList<>();

		public boolean matches(ComponentModel component, Function<ComponentModel, String> domIdReader,
				Function<ComponentModel, String> classReader) {

			// Cheap in-memory checks first, so property-tree reads only happen for
			// components that could still match.

			// 1. Component type
			if (tag != null || fullType != null) {
				String type = component.getType();
				if (type == null) {
					return false;
				}
				if (fullType != null && !type.equalsIgnoreCase(fullType)) {
					return false;
				}
				if (tag != null) {
					String lowerType = type.toLowerCase();
					String lowerTag = tag.toLowerCase();
					if (!lowerType.endsWith("." + lowerTag) && !lowerType.equals(lowerTag)) {
						return false;
					}
				}
			}

			// 2. Component name ([name="..."])
			if (name != null && !name.equals(component.getName())) {
				return false;
			}

			// 3. Explicit [type="..."]
			if (attrType != null && !attrType.equalsIgnoreCase(component.getType())) {
				return false;
			}

			// 4. DomId (#id and/or [domId="..."]), read only if this clause checks it
			if (domId != null || attrDomId != null) {
				String actualDomId = domIdReader.apply(component);
				if (domId != null && !domId.equals(actualDomId)) {
					return false;
				}
				if (attrDomId != null && !attrDomId.equals(actualDomId)) {
					return false;
				}
			}

			// 5. Style classes (.a.b), read only if this clause checks them
			if (!classNames.isEmpty()) {
				String actualClasses = classReader.apply(component);
				if (actualClasses == null) {
					return false;
				}
				List<String> classList = Arrays.asList(actualClasses.trim().split("\\s+"));
				if (!classList.containsAll(classNames)) {
					return false;
				}
			}

			return true;
		}
	}

	public static Predicate<ComponentModel> compile(String selector, Function<ComponentModel, String> domIdReader,
			Function<ComponentModel, String> classReader) {
		if (selector == null || selector.trim().isEmpty()) {
			return comp -> false;
		}

		List<Clause> clauses = parse(selector);
		return comp -> {
			for (Clause clause : clauses) {
				if (clause.matches(comp, domIdReader, classReader)) {
					return true;
				}
			}
			return false;
		};
	}

	public static List<Clause> parse(String selector) {
		List<Clause> clauses = new ArrayList<>();
		for (String raw : splitOnTopLevelCommas(selector)) {
			String trimmed = raw.trim();
			if (trimmed.isEmpty()) {
				throw invalid(selector, "empty clause between commas");
			}
			clauses.add(parseClause(trimmed, selector));
		}
		return Collections.unmodifiableList(clauses);
	}

	/**
	 * Splits on commas that are not inside [...] or quotes, so '[name="a,b"]' stays whole.
	 */
	private static List<String> splitOnTopLevelCommas(String selector) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		char quote = 0;
		boolean inBracket = false;
		for (char ch : selector.toCharArray()) {
			if (quote != 0) {
				if (ch == quote) {
					quote = 0;
				}
			} else if (ch == '"' || ch == '\'') {
				quote = ch;
			} else if (ch == '[') {
				inBracket = true;
			} else if (ch == ']') {
				inBracket = false;
			} else if (ch == ',' && !inBracket) {
				parts.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(ch);
		}
		parts.add(current.toString());
		return parts;
	}

	private static Clause parseClause(String s, String selector) {
		Clause clause = new Clause();
		int n = s.length();
		int i = 0;

		// Optional leading type: '*', a short type, or a dotted full type (+ classes)
		if (s.charAt(0) == '*') {
			i = 1;
		} else if (isIdentChar(s.charAt(0))) {
			int start = i;
			while (i < n && (isIdentChar(s.charAt(i)) || s.charAt(i) == '.')) {
				i++;
			}
			String[] segments = s.substring(start, i).split("\\.", -1);
			for (String segment : segments) {
				if (segment.isEmpty()) {
					throw invalid(selector, "empty name in '" + s.substring(start, i) + "'");
				}
			}
			int typeSegments = segments.length >= FULL_TYPE_SEGMENTS ? FULL_TYPE_SEGMENTS : 1;
			String type = String.join(".", Arrays.copyOfRange(segments, 0, typeSegments));
			if (typeSegments == FULL_TYPE_SEGMENTS) {
				clause.fullType = type;
			} else {
				clause.tag = type;
			}
			clause.classNames.addAll(Arrays.asList(segments).subList(typeSegments, segments.length));
		}

		while (i < n) {
			char ch = s.charAt(i);
			if (ch == '#') {
				int start = ++i;
				i = readIdent(s, i);
				if (i == start) {
					throw invalid(selector, "'#' must be followed by an id");
				}
				if (clause.domId != null) {
					throw invalid(selector, "only one #id is allowed per clause");
				}
				clause.domId = s.substring(start, i);
			} else if (ch == '.') {
				int start = ++i;
				i = readIdent(s, i);
				if (i == start) {
					throw invalid(selector, "'.' must be followed by a class name");
				}
				clause.classNames.add(s.substring(start, i));
			} else if (ch == '[') {
				i = parseAttribute(s, i, clause, selector);
			} else if (Character.isWhitespace(ch) || ch == '>' || ch == '+' || ch == '~') {
				throw invalid(selector, "combinators (space, >, +, ~) are not supported");
			} else if (ch == ':') {
				throw invalid(selector, "pseudo-classes are not supported");
			} else {
				throw invalid(selector, "unexpected character '" + ch + "'");
			}
		}
		return clause;
	}

	/**
	 * Parses '[key=value]' starting at the '[' and returns the index just past ']'.
	 */
	private static int parseAttribute(String s, int i, Clause clause, String selector) {
		int n = s.length();
		i = skipWhitespace(s, i + 1);

		int keyStart = i;
		i = readIdent(s, i);
		if (i == keyStart) {
			throw invalid(selector, "attribute name expected after '['");
		}
		String key = s.substring(keyStart, i);
		i = skipWhitespace(s, i);

		if (i >= n || s.charAt(i) != '=') {
			throw invalid(selector, "only exact [attr=value] matching is supported");
		}
		i = skipWhitespace(s, i + 1);

		String value;
		if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
			char quote = s.charAt(i);
			int end = s.indexOf(quote, i + 1);
			if (end < 0) {
				throw invalid(selector, "unterminated quote in attribute value");
			}
			value = s.substring(i + 1, end);
			i = end + 1;
		} else {
			int valueStart = i;
			while (i < n && (isIdentChar(s.charAt(i)) || s.charAt(i) == '.')) {
				i++;
			}
			value = s.substring(valueStart, i);
		}
		i = skipWhitespace(s, i);
		if (i >= n || s.charAt(i) != ']') {
			throw invalid(selector, "expected ']' to close attribute");
		}

		switch (key.toLowerCase()) {
			case "name":
				clause.name = value;
				break;
			case "domid":
				clause.attrDomId = value;
				break;
			case "type":
				clause.attrType = value;
				break;
			default:
				throw invalid(selector, "unsupported attribute [" + key + "]; supported: name, domId, type");
		}
		return i + 1;
	}

	private static int readIdent(String s, int i) {
		while (i < s.length() && isIdentChar(s.charAt(i))) {
			i++;
		}
		return i;
	}

	private static int skipWhitespace(String s, int i) {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return i;
	}

	private static boolean isIdentChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '_' || ch == '-';
	}

	private static IllegalArgumentException invalid(String selector, String reason) {
		return new IllegalArgumentException("Invalid selector '" + selector + "': " + reason);
	}
}
