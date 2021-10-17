package org.jresearch.k8s.operator.istio.certmanager.gateway;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.quarkus.logging.Log;
import me.snowdrop.istio.api.networking.v1beta1.GatewaySpec;
import me.snowdrop.istio.api.networking.v1beta1.Server;

@Singleton
@Controller
public class GatewayController implements ResourceController<Gateway> {

	// public static final String KIND = "CustomService";

	@Inject
	KubernetesClient kubernetesClient;

	@Override
	public DeleteControl deleteResource(Gateway resource, Context<Gateway> context) {
		Log.infof("Execution deleteResource for: %s", resource.getMetadata().getName());
		return DeleteControl.DEFAULT_DELETE;
	}

	@Override
	public UpdateControl<Gateway> createOrUpdateResource(Gateway resource, Context<Gateway> context) {
		Log.infof("Execution createOrUpdateResource for: %s", resource.getMetadata().getName());

		Optional.of(resource)
			.map(Gateway::getSpec)
			.map(GatewaySpec::getServers)
			.orElseGet(List::of)
			.stream()
			.findAny()
			.map(Server::getHosts)
			.orElseGet(List::of)
			.stream()
			.findAny()
			.ifPresentOrElse(
				host -> Log.infof("Issue certificate for: %s", host),
				() -> Log.infof("No certificate issued for: %s", resource.getMetadata().getName()));

		return UpdateControl.noUpdate();
	}
}