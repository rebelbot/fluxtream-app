package org.fluxtream.connectors.withings;

import org.fluxtream.OutsideTimeBoundariesException;
import org.fluxtream.TimeInterval;
import org.fluxtream.connectors.vos.AbstractInstantFacetVO;
import org.fluxtream.domain.GuestSettings;

public class WithingsBPMMeasureFacetVO extends AbstractInstantFacetVO<WithingsBPMMeasureFacet> {

	float systolic, diastolic, pulse;
	
	@Override
	public void fromFacet(WithingsBPMMeasureFacet facet, TimeInterval timeInterval,
			GuestSettings settings) throws OutsideTimeBoundariesException {
        this.start = facet.start;
		systolic = facet.systolic;
		diastolic = facet.diastolic;
		pulse = facet.heartPulse;
		description = facet.systolic + "/" + facet.diastolic + " mmHg";
	}

}
