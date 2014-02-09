/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.schema.compiler;

import java.net.URI;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

/**
 *
 * @author Christian Morgner (christian@morgner.de)
 */
public class CharSequenceJavaFileObject extends SimpleJavaFileObject {

	private String className     = null;
	private CharSequence content = null;

	/**
	 * This constructor will store the source code in the internal "content"
	 * variable and register it as a source code, using a URI containing the
	 * class full name
	 *
	 * @param className name of the public class in the source code
	 * @param content source code to compile
	 */
	public CharSequenceJavaFileObject(final String className, final CharSequence content) {
		
		super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
		
		this.content   = content;
		this.className = className;
	}

	/**
	 * Answers the CharSequence to be compiled. It will give the source code
	 * stored in variable "content"
	 */
	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return content;
	}
	
	public String getClassName() {
		return className;
	}
}