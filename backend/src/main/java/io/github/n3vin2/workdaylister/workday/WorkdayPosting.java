package io.github.n3vin2.workdaylister.workday;

/**
 * One Job Posting as Workday's jobs endpoint lists it, before a Scrape Run stores it.
 *
 * @param title the posting's title
 * @param externalPath the path of the posting under its Career Site, ending in
 *     {@code _<requisition ID>}, for example {@code /job/US-CA-Santa-Clara/Engineer_JR1990000}
 * @param locationsText the location as displayed, for example {@code US, CA, Santa Clara} or
 *     {@code 2 Locations}; may be absent
 * @param postedOn Workday's relative label, for example {@code Posted Today}; a localised string,
 *     never a date (ADR-0001)
 */
public record WorkdayPosting(
        String title, String externalPath, String locationsText, String postedOn) {

    /**
     * The requisition ID that identifies this posting within its Career Site: what follows the last
     * underscore of the external path.
     */
    public String requisitionId() {
        return externalPath.substring(externalPath.lastIndexOf('_') + 1);
    }
}
