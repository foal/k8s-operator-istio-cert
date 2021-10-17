package org.jresearch.k8s.operator.istio.certmanager;

import javax.inject.Inject;

import org.jresearch.k8s.operator.istio.certmanager.gateway.GatewayController;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class IstioCertManagerOperator implements QuarkusApplication {

	@Inject
	Operator operator;
	@Inject
	GatewayController gatewayController;

	@Override
	public int run(String... args) throws Exception {
		Log.info("Start operator");
		operator.register(gatewayController);
		operator.start();
		Quarkus.waitForExit();
		return 0;
	}

}