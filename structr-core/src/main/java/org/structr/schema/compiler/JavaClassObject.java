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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import javax.tools.SimpleJavaFileObject;

/**
 *
 * @author Christian Morgner (christian@morgner.de)
 */
public class JavaClassObject extends SimpleJavaFileObject {

	/**
	 * Byte code created by the compiler will be stored in this
	 * ByteArrayOutputStream so that we can later get the byte array out of
	 * it and put it in the memory as an instance of our class.
	 */
	protected final ByteArrayOutputStream bos =
		new ByteArrayOutputStream();

	/**
	 * Registers the compiled class object under URI containing the class
	 * full name
	 *
	 * @param name Full name of the compiled class
	 * @param kind Kind of the data. It will be CLASS in our case
	 */
	public JavaClassObject(String name, Kind kind) {
		super(URI.create("string:///" + name.replace('.', '/')
			+ kind.extension), kind);
	}

	/**
	 * Will be used by our file manager to get the byte code that can be put
	 * into memory to instantiate our class
	 *
	 * @return compiled byte code
	 */
	public byte[] getBytes() {
		return bos.toByteArray();
	}

	/**
	 * Will provide the compiler with an output stream that leads to our
	 * byte array. This way the compiler will write everything into the byte
	 * array that we will instantiate later
	 */
	@Override
	public OutputStream openOutputStream() throws IOException {
		return bos;
	}
}