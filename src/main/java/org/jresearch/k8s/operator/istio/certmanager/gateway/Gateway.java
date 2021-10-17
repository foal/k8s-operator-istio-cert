package org.jresearch.k8s.operator.istio.certmanager.gateway;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import me.snowdrop.istio.api.networking.v1beta1.GatewaySpec;

@Group("networking.istio.io")
@Version("v1beta1")
public class Gateway extends CustomResource<GatewaySpec, Void> implements Namespaced {
}