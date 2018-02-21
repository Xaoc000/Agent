package org.eclipse.iofog.field_agent;

import org.eclipse.iofog.command_line.util.CommandShellExecutor;
import org.eclipse.iofog.command_line.util.CommandShellResultSet;
import org.eclipse.iofog.field_agent.enums.VersionCommand;
import org.eclipse.iofog.field_agent.exceptions.UnknownVersionCommandException;
import org.eclipse.iofog.utils.logging.LoggingService;

import javax.json.JsonObject;
import java.io.File;
import java.util.List;

import static org.eclipse.iofog.field_agent.enums.VersionCommand.ROLLBACK;
import static org.eclipse.iofog.field_agent.enums.VersionCommand.UPGRADE;
import static org.eclipse.iofog.field_agent.enums.VersionCommand.parseJson;
import static org.eclipse.iofog.utils.Constants.SNAP_COMMON;

public class VersionHandler {

	private static final String MODULE_NAME = "Version Handler";

	public static String BACKUPS_DIR = SNAP_COMMON + "/var/backups/iofog";
	public static String MAX_RESTARTING_TIMEOUT = "60";

	private static String GET_LINUX_DISTRIBUTIVE_NAME = "cat /etc/os-release| grep = | awk -F\"[=]\" '{print $2}' | sed -n 1p";
	public static String GET_IOFOG_PACKAGE_INSTALLED_VERSION;
	public static String GET_IOFOG_PACKAGE_CANDIDATE_VERSION;

	static {
		String distrName = getDistributiveName();
		if (distrName.contains("Ubuntu") || distrName.contains("Debian") || distrName.contains("Raspbian")) {
			GET_IOFOG_PACKAGE_INSTALLED_VERSION = "apt-cache policy iofog | grep Installed | awk '{print $2}'";
			GET_IOFOG_PACKAGE_CANDIDATE_VERSION = "apt-cache policy iofog | grep Candidate | awk '{print $2}'";

		} else if (distrName.contains("Fedora") || distrName.contains("Red Hat") || distrName.contains("CentOS")) {
			GET_IOFOG_PACKAGE_INSTALLED_VERSION = "dnf list iofog | grep iofog | awk '{print $2}' | sed -n 1p";
			GET_IOFOG_PACKAGE_CANDIDATE_VERSION = "dnf list iofog | grep iofog | awk '{print $2}' | sed -n 2p";
		} else {
			LoggingService.logWarning(MODULE_NAME, "it looks like your distributive is not supported");
		}
	}

	private static String getDistributiveName() {
		CommandShellResultSet<List<String>, List<String>> resultSet = CommandShellExecutor.executeCommand(GET_LINUX_DISTRIBUTIVE_NAME);
		return resultSet.getValue().get(0);
	}

	public static String getFogInstalledVersion() {
		CommandShellResultSet<List<String>, List<String>> resultSet = CommandShellExecutor.executeCommand(GET_IOFOG_PACKAGE_INSTALLED_VERSION);
		return parseVersionResult(resultSet);
	}

	public static String  getFogCandidateVersion() {
		CommandShellResultSet<List<String>, List<String>> resultSet = CommandShellExecutor.executeCommand(GET_IOFOG_PACKAGE_CANDIDATE_VERSION);
		return parseVersionResult(resultSet);
	}

	private static String parseVersionResult(CommandShellResultSet<List<String>, List<String>> resultSet) {
		return resultSet.getError().size() == 0 ? resultSet.getValue().get(0) : "";
	}

	/**
	 * performs change version operation, received from ioFog controller
	 *
	 */
	public static void changeVersion(JsonObject actionData) {
		LoggingService.logInfo(MODULE_NAME, "trying to change version action");

		try{

			VersionCommand versionCommand = parseJson(actionData);
			String provisionKey = actionData.getString("provisionKey");

			if (isValidChangeVersionOperation(versionCommand)) {
				executeChangeVersionScript(versionCommand, provisionKey);
			}

		} catch (UnknownVersionCommandException e) {
			LoggingService.logWarning(MODULE_NAME, e.getMessage());
		}
	}

	/**
	 * executes sh script to change iofog version
	 *
	 * @param command {@link VersionCommand}
	 * @param provisionKey new provision key (used to restart iofog correctly)
	 */
	private static void executeChangeVersionScript(VersionCommand command, String provisionKey) {

		String shToExecute = command.getScript();

		String[] shArgs = {
				provisionKey,
				MAX_RESTARTING_TIMEOUT
		};

		CommandShellExecutor.executeScript(shToExecute, shArgs);
	}

	private static boolean isValidChangeVersionOperation(VersionCommand command) {
		switch (command){
			case UPGRADE:
				return isReadyToUpgrade();
			case ROLLBACK:
				return isReadyToRollback();
			default:
				return false;
		}
	}

	public static boolean isReadyToUpgrade() {
		return !(getFogInstalledVersion().equals(getFogCandidateVersion()));
	}

	public static boolean isReadyToRollback() {
		String[] backupsFiles = new File(BACKUPS_DIR).list();
		return !(backupsFiles == null || backupsFiles.length == 0);
	}
}