package org.structr.files.cmis.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.structr.cmis.common.CMISExtensionsData;

/**
 *
 * @author Christian Morgner
 */

public class StructrFileActions extends CMISExtensionsData implements AllowableActions {

	private static final StructrFileActions INSTANCE = new StructrFileActions();
	private static final Set<Action> actions         = new LinkedHashSet<>();

	static {

		actions.add(Action.CAN_DELETE_OBJECT);
		actions.add(Action.CAN_UPDATE_PROPERTIES);
//		actions.add(Action.CAN_GET_FOLDER_TREE);
		actions.add(Action.CAN_GET_PROPERTIES);
		actions.add(Action.CAN_GET_OBJECT_RELATIONSHIPS);
		actions.add(Action.CAN_GET_OBJECT_PARENTS);
		actions.add(Action.CAN_GET_FOLDER_PARENT);
		actions.add(Action.CAN_GET_DESCENDANTS);
		actions.add(Action.CAN_MOVE_OBJECT);
		actions.add(Action.CAN_DELETE_CONTENT_STREAM);
		actions.add(Action.CAN_CHECK_OUT);
		actions.add(Action.CAN_CANCEL_CHECK_OUT);
		actions.add(Action.CAN_CHECK_IN);
		actions.add(Action.CAN_SET_CONTENT_STREAM);
		actions.add(Action.CAN_GET_ALL_VERSIONS);
		actions.add(Action.CAN_ADD_OBJECT_TO_FOLDER);
		actions.add(Action.CAN_REMOVE_OBJECT_FROM_FOLDER);
		actions.add(Action.CAN_GET_CONTENT_STREAM);
		actions.add(Action.CAN_APPLY_POLICY);
		actions.add(Action.CAN_GET_APPLIED_POLICIES);
		actions.add(Action.CAN_REMOVE_POLICY);
		actions.add(Action.CAN_GET_CHILDREN);
//		actions.add(Action.CAN_CREATE_DOCUMENT);
//		actions.add(Action.CAN_CREATE_FOLDER);
		actions.add(Action.CAN_CREATE_RELATIONSHIP);
		actions.add(Action.CAN_CREATE_ITEM);
		actions.add(Action.CAN_DELETE_TREE);
		actions.add(Action.CAN_GET_RENDITIONS);
		actions.add(Action.CAN_GET_ACL);
		actions.add(Action.CAN_APPLY_ACL);
	}

	private StructrFileActions() {
	}

	@Override
	public Set<Action> getAllowableActions() {
		return actions;
	}

	public static StructrFileActions getInstance() {
		return INSTANCE;
	}
}