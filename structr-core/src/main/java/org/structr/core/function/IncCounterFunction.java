/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.function;

import org.structr.common.error.FrameworkException;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class IncCounterFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_INC_COUNTER = "Usage: ${inc_counter(level[, resetLowerLevels])}. Example: ${inc_counter(1, true)}";

	@Override
	public String getName() {
		return "inc_counter()";
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		if (arrayHasMinLengthAndMaxLengthAndAllElementsNotNull(sources, 1, 2)) {

			try {

				final int level = parseInt(sources[0]);

				ctx.incrementCounter(level);

				// reset lower levels?
				if (sources.length == 2 && "true".equals(sources[1].toString())) {

					// reset lower levels
					for (int i = level + 1; i < 10; i++) {
						ctx.resetCounter(i);
					}
				}

			} catch (NumberFormatException nfe) {

				logException(nfe, "{}: NumberFormatException parsing counter level \"{}\" in element \"{}\". Parameters: {}", new Object[] { getName(), sources[0].toString(), caller, getParametersAsString(sources) });

			}

		} else {

			logParameterError(caller, sources, ctx.isJavaScriptContext());

		}

		return "";
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return ERROR_MESSAGE_INC_COUNTER;
	}

	@Override
	public String shortDescription() {
		return "Increases the value of the counter with the given index";
	}

}
