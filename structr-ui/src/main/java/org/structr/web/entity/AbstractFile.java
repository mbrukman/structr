/**
 * Copyright (C) 2010-2017 Structr GmbH
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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.entity;


import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.Predicate;
import org.structr.api.config.Settings;
import org.structr.api.search.SortType;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.UniqueToken;
import org.structr.core.Export;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.property.AbstractReadOnlyProperty;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.CollectionIdProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.EntityIdProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.StartNode;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.relation.FileChildren;
import org.structr.web.entity.relation.FileSiblings;
import org.structr.web.entity.relation.FolderChildren;
import org.structr.web.property.PathProperty;

/**
 * Base class for filesystem objects in structr.
 *
 *
 */
public class AbstractFile extends LinkedTreeNode<FileChildren, FileSiblings, AbstractFile> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractFile.class.getName());

	public static final Property<Folder> parent                    = new StartNode<>("parent", FolderChildren.class);
	public static final Property<List<AbstractFile>> children      = new EndNodes<>("children", FileChildren.class);
	public static final Property<AbstractFile> previousSibling     = new StartNode<>("previousSibling", FileSiblings.class);
	public static final Property<AbstractFile> nextSibling         = new EndNode<>("nextSibling", FileSiblings.class);
	public static final Property<List<String>> childrenIds         = new CollectionIdProperty("childrenIds", children);
	public static final Property<String> nextSiblingId             = new EntityIdProperty("nextSiblingId", nextSibling);
	public static final Property<String> path                      = new PathProperty("path").indexed().readOnly();
	public static final Property<String> parentId                  = new EntityIdProperty("parentId", parent);
	public static final Property<Boolean> hasParent                = new BooleanProperty("hasParent").indexed();

	public static final Property<Long> lastSeenMounted             = new LongProperty("lastSeenMounted");
	public static final Property<Boolean> isExternal               = new BooleanProperty("isExternal").writeOnce();
	public static final Property<Boolean> isMounted                = new MethodCallProperty<>("isMounted", AbstractFile.class, "isMounted");

	public static final Property<Boolean>  includeInFrontendExport = new BooleanProperty("includeInFrontendExport").cmis().indexed();

	public static final View defaultView = new View(AbstractFile.class, PropertyView.Public,
		path, isExternal, isMounted, lastSeenMounted
	);

	public static final View uiView = new View(AbstractFile.class, PropertyView.Ui,
		path, isExternal, isMounted, lastSeenMounted
	);

	@Override
	public boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		boolean valid = true;

		if (Settings.UniquePaths.getValue()) {
			valid = validateAndRenameFileOnce(securityContext, errorBuffer);
		}

		return valid && super.onCreation(securityContext, errorBuffer);
	}

	@Override
	public boolean onModification(final SecurityContext securityContext, final ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		boolean valid = true;

		if (Settings.UniquePaths.getValue()) {
			valid = validateAndRenameFileOnce(securityContext, errorBuffer);
		}

		return valid && super.onModification(securityContext, errorBuffer, modificationQueue);
	}

	public boolean validatePath(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		final String filePath = getProperty(path);
		if (filePath != null) {

			final List<AbstractFile> files = StructrApp.getInstance().nodeQuery(AbstractFile.class).and(path, filePath).getAsList();
			for (final AbstractFile file : files) {

				if (!file.getUuid().equals(getUuid())) {

					if (errorBuffer != null) {

						final UniqueToken token = new UniqueToken(AbstractFile.class.getSimpleName(), path, file.getUuid());
						token.setValue(filePath);

						errorBuffer.add(token);
					}

					return false;
				}
			}
		}

		return true;
	}

	public boolean validateAndRenameFileOnce(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		boolean valid = validatePath(securityContext, null);

		if (!valid) {

			final String originalPath = getProperty(AbstractFile.path);
			final String newName = getProperty(AbstractFile.name).concat("_").concat(FileHelper.getDateString());

			setProperty(AbstractFile.name, newName);

			valid = validatePath(securityContext, errorBuffer);

			if (valid) {
				logger.warn("File {} already exists, renaming to {}", new Object[] { originalPath, newName });
			} else {
				logger.warn("File {} already existed. Tried renaming to {} and failed. Aborting.", new Object[] { originalPath, newName });
			}

		}

		return valid;

	}

	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {

		boolean valid = super.isValid(errorBuffer);

		valid &= nonEmpty(AbstractFile.name, errorBuffer);
		valid &= ValidationHelper.isValidStringMatchingRegex(this, name, "[^\\/\\x00]+", errorBuffer);

		return valid;
	}

	@Override
	public Class<FileChildren> getChildLinkType() {
		return FileChildren.class;
	}

	@Override
	public Class<FileSiblings> getSiblingLinkType() {
		return FileSiblings.class;
	}

	public String getFolderPath() {

		LinkedTreeNode parentFolder = getProperty(AbstractFile.parent);
		String folderPath           = getProperty(AbstractFile.name);

		if (folderPath == null) {
			folderPath = getProperty(GraphObject.id);
		}

		while (parentFolder != null) {

			folderPath   = parentFolder.getName().concat("/").concat(folderPath);
			parentFolder = parentFolder.getProperty(AbstractFile.parent);
		}

		return "/".concat(folderPath);
	}

	public boolean includeInFrontendExport() {

		if (getProperty(FileBase.includeInFrontendExport)) {

			return true;
		}

		final Folder _parent = getProperty(FileBase.parent);
		if (_parent != null) {

			// recurse
			return _parent.includeInFrontendExport();
		}

		return false;
	}

	public boolean isMounted() {

		if (getProperty(Folder.mountTarget) != null) {
			return true;
		}

		final Folder parent = getProperty(AbstractFile.parent);
		if (parent != null) {

			return parent.isMounted();
		}

		return false;
	}

	public boolean isExternal() {
		return getProperty(isExternal);
	}

	@Export
	public boolean isBinaryDataAccessible() {
		return !isExternal() || isMounted();
	}

	// ----- protected methods -----
	protected static java.io.File defaultGetFileOnDisk(final FileBase fileBase, final boolean create) {

		final String uuid       = fileBase.getUuid();
		final String filePath   = Settings.FilesPath.getValue();
		final String uuidPath   = FileBase.getDirectoryPath(uuid);
		final java.io.File file = new java.io.File(filePath + "/" + uuidPath + "/" + uuid);

		// create parent directory tree
		file.getParentFile().mkdirs();

		// create file only if requested
		if (create && !file.exists() && !fileBase.isExternal()) {

			try {

				file.createNewFile();

			} catch (IOException ioex) {

				logger.error("Unable to create file {}: {}", file, ioex.getMessage());
			}
		}

		return file;
	}

	// ----- nested classes -----
	private static class MethodCallProperty<T> extends AbstractReadOnlyProperty<T> {

		private Method method = null;

		public MethodCallProperty(final String name, final Class type, final String methodName) {

			super(name);

			try {

				this.method = type.getDeclaredMethod(methodName);

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		@Override
		public Class valueType() {
			return Boolean.class;
		}

		@Override
		public Class relatedType() {
			return null;
		}

		@Override
		public T getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter) {
			return getProperty(securityContext, obj, applyConverter, null);
		}

		@Override
		public T getProperty(SecurityContext securityContext, GraphObject obj, boolean applyConverter, Predicate<GraphObject> predicate) {

			try {

				return (T)method.invoke(obj);

			} catch (Throwable t) {
				logger.warn("Unable to call method {}: {}", method.getName(), t.getMessage());
			}

			return null;
		}

		@Override
		public boolean isCollection() {
			return false;
		}

		@Override
		public SortType getSortType() {
			return SortType.Default;
		}
	}
}
