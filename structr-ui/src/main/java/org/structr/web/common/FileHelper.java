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
package org.structr.web.common;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.PropertyMap;
import org.structr.util.Base64;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.FileBase;
import static org.structr.web.entity.FileBase.size;
import org.structr.web.entity.Folder;
import org.structr.web.entity.Image;

/**
 * File utility class.
 */
public class FileHelper {

	private static final String UNKNOWN_MIME_TYPE         = "application/octet-stream";
	private static final Logger logger                    = LoggerFactory.getLogger(FileHelper.class.getName());
	private static final MimetypesFileTypeMap mimeTypeMap = new MimetypesFileTypeMap(FileHelper.class.getResourceAsStream("/mime.types"));

	/**
	 * Transform an existing file into the target class.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param uuid
	 * @param fileType
	 * @return transformed file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T transformFile(final SecurityContext securityContext, final String uuid, final Class<T> fileType) throws FrameworkException, IOException {

		AbstractFile existingFile = getFileByUuid(securityContext, uuid);

		if (existingFile != null) {

			existingFile.unlockSystemPropertiesOnce();
			existingFile.setProperties(securityContext, new PropertyMap(AbstractNode.type, fileType == null ? org.structr.dynamic.File.class.getSimpleName() : fileType.getSimpleName()));

			existingFile = getFileByUuid(securityContext, uuid);

			return (T)(fileType != null ? fileType.cast(existingFile) : (org.structr.dynamic.File) existingFile);
		}

		return null;
	}

	/**
	 * Create a new image node from image data encoded in base64 format.
	 *
	 * If the given string is an uuid of an existing file, transform it into
	 * the target class.
	 *
	 * @param <T>
	 * @param securityContext
	 * @param rawData
	 * @param t defaults to File.class if null
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T createFileBase64(final SecurityContext securityContext, final String rawData, final Class<T> t) throws FrameworkException, IOException {

		Base64URIData uriData = new Base64URIData(rawData);

		return createFile(securityContext, uriData.getBinaryData(), uriData.getContentType(), t);

	}

	/**
	 * Create a new file node from the given input stream
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileStream
	 * @param contentType
	 * @param fileType defaults to File.class if null
	 * @param name
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T createFile(final SecurityContext securityContext, final InputStream fileStream, final String contentType, final Class<T> fileType, final String name)
			throws FrameworkException, IOException {

		PropertyMap props = new PropertyMap();

		props.put(AbstractNode.name, name);

		T newFile = (T) StructrApp.getInstance(securityContext).create(fileType, props);

		setFileData(newFile, fileStream, contentType);

		// schedule indexing
		newFile.notifyUploadCompletion();

		return newFile;

	}

	/**
	 * Create a new file node from the given input stream and sets the parentFolder
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileStream
	 * @param contentType
	 * @param fileType defaults to File.class if null
	 * @param name
	 * @param parentFolder
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T createFile(final SecurityContext securityContext, final InputStream fileStream, final String contentType, final Class<T> fileType, final String name, final Folder parentFolder)
		throws FrameworkException, IOException {

		PropertyMap props = new PropertyMap();

		props.put(AbstractNode.name, name);

		if (parentFolder != null) {

			props.put(FileBase.hasParent, true);
			props.put(FileBase.parent, parentFolder);

		}

		T newFile = (T) StructrApp.getInstance(securityContext).create(fileType, props);

		setFileData(newFile, fileStream, contentType);

		// schedule indexing
		newFile.notifyUploadCompletion();

		return newFile;

	}

	/**
	 * Create a new file node from the given byte array
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileData
	 * @param contentType if null, try to auto-detect content type
	 * @param t
	 * @param name
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T createFile(final SecurityContext securityContext, final byte[] fileData, final String contentType, final Class<T> t, final String name)
		throws FrameworkException, IOException {

		PropertyMap props = new PropertyMap();

		props.put(AbstractNode.name, name);

		T newFile = (T) StructrApp.getInstance(securityContext).create(t, props);

		setFileData(newFile, fileData, contentType);

		// schedule indexing
		newFile.notifyUploadCompletion();

		return newFile;
	}

	/**
	 * Create a new file node from the given byte array
	 *
	 * @param <T>
	 * @param securityContext
	 * @param fileData
	 * @param contentType
	 * @param t defaults to File.class if null
	 * @return file
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static <T extends org.structr.web.entity.FileBase> T createFile(final SecurityContext securityContext, final byte[] fileData, final String contentType, final Class<T> t)
		throws FrameworkException, IOException {

		return createFile(securityContext, fileData, contentType, t, null);

	}

	/**
	 * Decodes base64-encoded raw data into binary data and writes it to the
	 * given file.
	 *
	 * @param file
	 * @param rawData
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void decodeAndSetFileData(final org.structr.dynamic.File file, final String rawData) throws FrameworkException, IOException {

		Base64URIData uriData = new Base64URIData(rawData);
		setFileData(file, uriData.getBinaryData(), uriData.getContentType());
	}

	/**
	 * Write image data from the given byte[] to the given file node and set checksum and size.
	 *
	 * @param file
	 * @param fileData
	 * @param contentType if null, try to auto-detect content type
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void setFileData(final FileBase file, final byte[] fileData, final String contentType) throws FrameworkException, IOException {

		FileHelper.writeToFile(file, fileData);
		setFileProperties(file, contentType);
	}

	/**
	 * Write image data from the given InputStream to the given file node and set checksum and size.
	 *
	 * @param file
	 * @param fileStream
	 * @param contentType if null, try to auto-detect content type
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void setFileData(final FileBase file, final InputStream fileStream, final String contentType) throws FrameworkException, IOException {

		FileHelper.writeToFile(file, fileStream);
		setFileProperties(file, contentType);
	}

	/**
	 * Set the contentType, checksum, size and version properties of the given fileNode
	 *
	 * @param file
	 * @param contentType if null, try to auto-detect content type
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void setFileProperties (final FileBase file, final String contentType) throws IOException, FrameworkException {

		final java.io.File fileOnDisk = file.getFileOnDisk(false);
		final PropertyMap map         = new PropertyMap();

		map.put(FileBase.contentType, contentType != null ? contentType : FileHelper.getContentMimeType(fileOnDisk, file.getProperty(FileBase.name)));

		map.putAll(getChecksums(file, fileOnDisk));

		map.put(FileBase.size,        FileHelper.getSize(fileOnDisk));
		map.put(FileBase.version,     1);

		file.setProperties(file.getSecurityContext(), map);
	}

	/**
	 * Set the uuid and the path of a newly created fileNode
	 *
	 * @param fileNode
	 * @throws FrameworkException
	 */
	public static void setFileProperties (FileBase fileNode) throws FrameworkException {

		final PropertyMap properties = new PropertyMap();

		String id = fileNode.getProperty(GraphObject.id);
		if (id == null) {

			final String newUuid = UUID.randomUUID().toString().replaceAll("[\\-]+", "");
			id = newUuid;

			fileNode.unlockSystemPropertiesOnce();
			properties.put(GraphObject.id, newUuid);
		}

		fileNode.unlockSystemPropertiesOnce();
		fileNode.setProperties(fileNode.getSecurityContext(), properties);

	}

	/**
	 * Calculate checksums that are configured in settings of parent folder.
	 *
	 * @param file
	 * @param fileOnDisk
	 * @return
	 * @throws IOException
	 */
	private static PropertyMap getChecksums(final FileBase file, final java.io.File fileOnDisk) throws IOException {

		final PropertyMap propreriesWithChecksums = new PropertyMap();

		Folder parentFolder = file.getProperty(FileBase.parent);
		String checksums = null;

		while (parentFolder != null && checksums == null) {

			checksums = parentFolder.getProperty(Folder.enabledChecksums);
			parentFolder = parentFolder.getProperty(FileBase.parent);
		}

		if (checksums == null) {
			checksums = Settings.DefaultChecksums.getValue();
		}

		if (StringUtils.contains(checksums, "crc32"))	{
			propreriesWithChecksums.put(FileBase.checksum,    FileHelper.getChecksum(fileOnDisk));
		}

		if (StringUtils.contains(checksums, "md5"))	{
			propreriesWithChecksums.put(FileBase.md5,         FileHelper.getMD5Checksum(file));
		}

		if (StringUtils.contains(checksums, "sha1"))	{
			propreriesWithChecksums.put(FileBase.sha1,        FileHelper.getSHA1Checksum(file));
		}

		if (StringUtils.contains(checksums, "sha512"))	{
			propreriesWithChecksums.put(FileBase.sha512,      FileHelper.getSHA512Checksum(file));
		}

		return propreriesWithChecksums;
	}
	/**
	 * Update checksums, content type, size and additional properties of the given file
	 *
	 * @param file the file
	 * @param map  additional properties
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final FileBase file, final PropertyMap map) throws FrameworkException {
		updateMetadata(file, map, false);
	}

	/**
	 * Update checksums (optional), content type, size and additional properties of the given file
	 *
	 * @param file the file
	 * @param map  additional properties
	 * @param calcChecksums
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final FileBase file, final PropertyMap map, final boolean calcChecksums) throws FrameworkException {

		final java.io.File fileOnDisk = file.getFileOnDisk(false);

		if (fileOnDisk != null && fileOnDisk.exists()) {

			try {

				String contentType = file.getContentType();

				// Don't overwrite existing MIME type
				if (StringUtils.isBlank(contentType)) {

					try {

						contentType = getContentMimeType(file);
						map.put(FileBase.contentType, contentType);

					} catch (IOException ex) {
						logger.debug("Unable to detect content MIME type", ex);
					}
				}

				map.put(FileBase.fileModificationDate, fileOnDisk.lastModified());

				if (calcChecksums) {
					map.putAll(getChecksums(file, fileOnDisk));
				}

				if (contentType != null) {

					// modify type when image type is detected
					if (contentType.startsWith("image/")) {
						map.put(FileBase.type, Image.class.getSimpleName());
					}
				}

				long fileSize = FileHelper.getSize(fileOnDisk);
				if (fileSize > 0) {

					map.put(size, fileSize);
				}

				file.unlockSystemPropertiesOnce();
				file.setProperties(file.getSecurityContext(), map);

			} catch (IOException ioex) {
				logger.warn("Unable to access {} on disk: {}", fileOnDisk, ioex.getMessage());
			}
		}
	}

	/**
	 * Update checksums, content type and size of the given file
	 *
	 * @param file the file
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final FileBase file) throws FrameworkException {
		updateMetadata(file, new PropertyMap(), false);
	}

	/**
	 * Update checksums, content type and size of the given file
	 *
	 * @param file the file
	 * @param calcChecksums
	 * @throws FrameworkException
	 */
	public static void updateMetadata(final FileBase file, final boolean calcChecksums) throws FrameworkException {
		updateMetadata(file, new PropertyMap(), calcChecksums);
	}

	public static String getBase64String(final FileBase file) {

		try {

			final InputStream is = file.getInputStream();
			if (is != null) {

				return Base64.encodeToString(IOUtils.toByteArray(is), false);
			}

		} catch (IOException ex) {
			logger.error("Could not get base64 string from file ", ex);
		}

		return null;
	}

	public static class Base64URIData {

		private String contentType = null;
		private String data = null;

		public Base64URIData(final String rawData) {

			if (rawData.contains(",")) {

				String[] parts = StringUtils.split(rawData, ",");
				if (parts.length == 2) {

					contentType = StringUtils.substringBetween(parts[0], "data:", ";base64");
					data        = parts[1];
				}

			} else {

				data = rawData;
			}
		}

		public String getContentType() {
			return contentType;
		}

		public String getData() {
			return data;
		}

		public byte[] getBinaryData() {
			return Base64.decode(data);
		}
	}

	/**
	 * Write binary data to a file and reference the file on disk at the
	 * given file node
	 *
	 * @param fileNode
	 * @param inStream
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void writeToFile(final org.structr.dynamic.File fileNode, final InputStream inStream) throws FrameworkException, IOException {

		writeToFile((FileBase) fileNode, inStream);

	}

	/**
	 * Write binary data from byte[] to a file and reference the file on disk at the
	 * given file node
	 *
	 * @param fileNode
	 * @param data
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void writeToFile(final FileBase fileNode, final byte[] data) throws FrameworkException, IOException {

		setFileProperties(fileNode);

		FileUtils.writeByteArrayToFile(fileNode.getFileOnDisk(), data);

	}

	/**
	 * Write binary data from FileInputStream to a file and reference the file on disk at the
	 * given file node
	 *
	 * @param fileNode
	 * @param data
	 * @throws FrameworkException
	 * @throws IOException
	 */
	public static void writeToFile(final FileBase fileNode, final InputStream data) throws FrameworkException, IOException {

		setFileProperties(fileNode);

		try (final FileOutputStream out = new FileOutputStream(fileNode.getFileOnDisk())) {

			IOUtils.copy(data, out);

			data.close();
		}
	}

	/**
	 * Return mime type of given file
	 *
	 * @param file
	 * @return content type
	 * @throws java.io.IOException
	 */
	public static String getContentMimeType(final FileBase file) throws IOException {
		return getContentMimeType(file.getFileOnDisk(), file.getProperty(AbstractNode.name));
	}

	/**
	 * Return mime type of given file
	 *
	 * @param file
	 * @param name
	 * @return content type
	 * @throws java.io.IOException
	 */
	public static String getContentMimeType(final java.io.File file, final String name) throws IOException {

		String mimeType;

		// try name first, if not null
		if (name != null) {
			mimeType = mimeTypeMap.getContentType(name);
			if (mimeType != null && !UNKNOWN_MIME_TYPE.equals(mimeType)) {
				return mimeType;
			}
		}

		final MediaType mediaType = new DefaultDetector().detect(new BufferedInputStream(new FileInputStream(file)), new Metadata());

		mimeType = mediaType.toString();
		if (mimeType != null) {

			return mimeType;
		}

		// no success :(
		return UNKNOWN_MIME_TYPE;
	}

	/**
	 * Find a file by its absolute ancestor path.
	 *
	 * File may not be hidden or deleted.
	 *
	 * @param securityContext
	 * @param absolutePath
	 * @return file
	 */
	public static AbstractFile getFileByAbsolutePath(final SecurityContext securityContext, final String absolutePath) {

		try {

			return StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).and(AbstractFile.path, absolutePath).getFirst();

		} catch (FrameworkException ex) {
			ex.printStackTrace();
			logger.warn("File not found: {}", absolutePath);
		}

		return null;
	}

	public static AbstractFile getFileByUuid(final SecurityContext securityContext, final String uuid) {

		logger.debug("Search for file with uuid: {}", uuid);

		try {
			return StructrApp.getInstance(securityContext).get(AbstractFile.class, uuid);

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file by UUID {}: {}", uuid, fex.getMessage());
		}

		return null;
	}

	public static AbstractFile getFirstFileByName(final SecurityContext securityContext, final String name) {

		logger.debug("Search for file with name: {}", name);

		try {
			return StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).andName(name).getFirst();

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file for name {}: {}", name, fex.getMessage());
		}

		return null;
	}

	/**
	 * Find the first file with given name on root level (without parent folder).
	 *
	 * @param securityContext
	 * @param name
	 * @return file
	 */
	public static AbstractFile getFirstRootFileByName(final SecurityContext securityContext, final String name) {

		logger.debug("Search for file with name: {}", name);

		try {
			final List<AbstractFile> files = StructrApp.getInstance(securityContext).nodeQuery(AbstractFile.class).andName(name).getAsList();

			for (final AbstractFile file : files) {

				if (file.getProperty(AbstractFile.parent) == null) {
					return file;
				}

			}

		} catch (FrameworkException fex) {

			logger.warn("Unable to find a file for name {}: {}", name, fex.getMessage());
		}

		return null;
	}

	/**
	 * Create one folder per path item and return the last folder.
	 *
	 * F.e.: /a/b/c => Folder["name":"a"] --HAS_CHILD--> Folder["name":"b"]
	 * --HAS_CHILD--> Folder["name":"c"], returns Folder["name":"c"]
	 *
	 * @param securityContext
	 * @param path
	 * @return folder
	 * @throws FrameworkException
	 */
	public static Folder createFolderPath(final SecurityContext securityContext, final String path) throws FrameworkException {

		final App app = StructrApp.getInstance(securityContext);

		if (path == null) {

			return null;
		}

		Folder folder = (Folder) FileHelper.getFileByAbsolutePath(securityContext, path);

		if (folder != null) {
			return folder;
		}

		String[] parts = PathHelper.getParts(path);
		String partialPath = "";

		for (String part : parts) {

			// ignore ".." and "." in paths
			if ("..".equals(part) || ".".equals(part)) {
				continue;
			}

			Folder parent = folder;

			partialPath += PathHelper.PATH_SEP + part;
			folder = (Folder) FileHelper.getFileByAbsolutePath(securityContext, partialPath);

			if (folder == null) {

				folder = app.create(Folder.class, part);

			}

			if (parent != null) {

				folder.setProperties(securityContext, new PropertyMap(AbstractFile.parent, parent));

			}
		}

		return folder;

	}

	public static String getDateString() {
		return new SimpleDateFormat("yyyy-MM-dd-HHmmssSSS").format(new Date());
	}

	public static Long getChecksum(final java.io.File fileOnDisk) throws IOException {
		return FileUtils.checksumCRC32(fileOnDisk);
	}

	public static String getMD5Checksum(final FileBase file) {

		try {
			return DigestUtils.md5Hex(file.getInputStream());

		} catch (final IOException ex) {
			logger.warn("Unable to calculate MD5 checksum of file represented by node " + file, ex);
		}

		return null;
	}

	public static String getMD5Checksum(final java.io.File fileOnDisk) {

		try {
			return DigestUtils.md5Hex(FileUtils.openInputStream(fileOnDisk));

		} catch (final IOException ex) {
			logger.warn("Unable to calculate MD5 checksum of file " + fileOnDisk, ex);
		}

		return null;
	}

	public static String getSHA1Checksum(final FileBase file) {

		try {
			return DigestUtils.sha1Hex(file.getInputStream());

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-1 checksum of file represented by node " + file, ex);
		}

		return null;
	}

	public static String getSHA1Checksum(final java.io.File fileOnDisk) {

		try {
			return DigestUtils.sha1Hex(FileUtils.openInputStream(fileOnDisk));

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-1 checksum of file " + fileOnDisk, ex);
		}

		return null;
	}

	public static String getSHA256Checksum(final FileBase file) {

		try {
			return DigestUtils.sha256Hex(file.getInputStream());

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-256 checksum of file represented by node " + file, ex);
		}

		return null;
	}

	public static String getSHA256Checksum(final java.io.File fileOnDisk) {

		try {
			return DigestUtils.sha256Hex(FileUtils.openInputStream(fileOnDisk));

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-256 checksum of file " + fileOnDisk, ex);
		}

		return null;
	}

	public static String getSHA384Checksum(final FileBase file) {

		try {
			return DigestUtils.sha384Hex(file.getInputStream());

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-384 checksum of file represented by node " + file, ex);
		}

		return null;
	}

	public static String getSHA384Checksum(final java.io.File fileOnDisk) {

		try {
			return DigestUtils.sha384Hex(FileUtils.openInputStream(fileOnDisk));

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-384 checksum of file " + fileOnDisk, ex);
		}

		return null;
	}

	public static String getSHA512Checksum(final FileBase file) {

		try {
			return DigestUtils.sha512Hex(file.getInputStream());

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-512 checksum of file represented by node " + file, ex);
		}

		return null;
	}

	public static String getSHA512Checksum(final java.io.File fileOnDisk) {

		try {
			return DigestUtils.sha512Hex(FileUtils.openInputStream(fileOnDisk));

		} catch (final IOException ex) {
			logger.warn("Unable to calculate SHA-512 checksum of file " + fileOnDisk, ex);
		}

		return null;
	}

	public static long getSize(final java.io.File file) {

		try {

			return file.length();

		} catch (Exception ex) {

			logger.warn("Could not calculate file size of file {}: {}", file.getPath(), ex.getMessage());
		}

		return -1;

	}
}
