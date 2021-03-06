/*
 * *******************************************************************************
 *  * Copyright (c) 2018 Edgeworx, Inc.
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Eclipse Public License v. 2.0 which is available at
 *  * http://www.eclipse.org/legal/epl-2.0
 *  *
 *  * SPDX-License-Identifier: EPL-2.0
 *  *******************************************************************************
 *
 */

package org.eclipse.iofog.diagnostics.strace;

import org.eclipse.iofog.command_line.util.CommandShellExecutor;
import org.eclipse.iofog.command_line.util.CommandShellResultSet;
import org.eclipse.iofog.utils.logging.LoggingService;

import javax.json.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.eclipse.iofog.utils.logging.LoggingService.logWarning;

public class StraceDiagnosticManger {

	private static final String MODULE_NAME = "STrace Diagnostic Manager";

	private final List<MicroserviceStraceData> monitoringMicroservices;
	private static StraceDiagnosticManger instance = null;

	public static StraceDiagnosticManger getInstance() {
		if (instance == null) {
			synchronized (StraceDiagnosticManger.class) {
				if (instance == null)
					instance = new StraceDiagnosticManger();
			}
		}
		return instance;
	}

	private StraceDiagnosticManger() {
		this.monitoringMicroservices = new CopyOnWriteArrayList<>();
	}

	public List<MicroserviceStraceData> getMonitoringMicroservices() {
		return monitoringMicroservices;
	}

	public void updateMonitoringMicroservices(JsonObject diagnosticData) {
		LoggingService.logInfo(MODULE_NAME, "trying to update strace monitoring microservices");

		if (diagnosticData.containsKey("straceValues")) {
			JsonArray straceMicroserviceChanges = diagnosticData.getJsonArray("straceValues");
			for (JsonValue microserviceValue : straceMicroserviceChanges) {
				JsonObject microservice = (JsonObject) microserviceValue;
				if (microservice.containsKey("microserviceUuid")) {
					String microserviceUuid = microservice.getString("microserviceUuid");
					boolean strace = microservice.getBoolean("straceRun");
					manageMicroservice(microserviceUuid, strace);
				}
			}
		}
	}

	private void manageMicroservice(String microserviceUuid, boolean strace) {
        if (strace) {
            enableMicroserviceStraceDiagnostics(microserviceUuid);
        } else {
            disableMicroserviceStraceDiagnostics(microserviceUuid);
        }
	}

	private Optional<MicroserviceStraceData> getStraceDataByMicroserviceUuid(String microserviceUuid) {
		return this.monitoringMicroservices.stream()
				.filter(microservice -> microservice.getMicroserviceUuid().equals(microserviceUuid))
				.findFirst();
	}

	public void enableMicroserviceStraceDiagnostics(String microserviceUuid) {
		try {
			int pid = getPidByMicroserviceUuid(microserviceUuid);
			MicroserviceStraceData newMicroserviceStraceData = new MicroserviceStraceData(microserviceUuid, pid, true);
			this.monitoringMicroservices.removeIf(
				oldMicroserviceStraceData -> oldMicroserviceStraceData.getMicroserviceUuid().equals(microserviceUuid)
			);
			this.monitoringMicroservices.add(newMicroserviceStraceData);
			runStrace(newMicroserviceStraceData);
		} catch (IllegalArgumentException e) {
			logWarning(MODULE_NAME, "Can't get pid of process");
		}
    }

	public void disableMicroserviceStraceDiagnostics(String microserviceUuid) {
        getStraceDataByMicroserviceUuid(microserviceUuid).ifPresent(microserviceStraceData -> {
            microserviceStraceData.setStraceRun(false);
            this.monitoringMicroservices.remove(microserviceStraceData);
        });
    }

	private int getPidByMicroserviceUuid(String microserviceUuid) throws IllegalArgumentException {
		CommandShellResultSet<List<String>, List<String>> resultSet = CommandShellExecutor.executeCommand("docker top " + microserviceUuid);

		if (resultSet.getValue() != null && resultSet.getValue().size() > 1 && resultSet.getValue().get(1) != null) {
			String pid = resultSet.getValue().get(1).split("\\s+")[1];
			return Integer.parseInt(pid);
		} else {
			throw new IllegalArgumentException();
		}
	}

	private void runStrace(MicroserviceStraceData microserviceStraceData) {
		String straceCommand = "strace -p " + microserviceStraceData.getPid();
		CommandShellResultSet<List<String>, List<String>> resultSet = new CommandShellResultSet<>(null, microserviceStraceData.getResultBuffer());
		CommandShellExecutor.executeDynamicCommand(straceCommand, resultSet, microserviceStraceData.getStraceRun());
	}

}
