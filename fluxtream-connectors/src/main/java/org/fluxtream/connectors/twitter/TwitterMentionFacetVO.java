package org.fluxtream.connectors.twitter;

import org.apache.commons.lang.StringEscapeUtils;
import org.fluxtream.OutsideTimeBoundariesException;
import org.fluxtream.TimeInterval;
import org.fluxtream.connectors.vos.AbstractInstantFacetVO;
import org.fluxtream.domain.GuestSettings;

public class TwitterMentionFacetVO extends AbstractInstantFacetVO<TwitterMentionFacet> {

	public String text;
	public String profileImageUrl;
	public String userName;
	
	@Override
	public void fromFacet(TwitterMentionFacet facet, TimeInterval timeInterval, GuestSettings settings) throws OutsideTimeBoundariesException {
		text = StringEscapeUtils.escapeHtml(facet.text);
		description = StringEscapeUtils.escapeHtml(facet.text);
		this.profileImageUrl = facet.profileImageUrl;
		this.userName = facet.userName;
	}

}
