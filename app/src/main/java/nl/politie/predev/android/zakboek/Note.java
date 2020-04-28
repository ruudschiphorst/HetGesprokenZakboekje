package nl.politie.predev.android.zakboek;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class Note implements Comparable<Note> {

    private Long id;
    private UUID noteID;
    private Integer version;
    private String title;
    private String note_text;
    private String owner;
    private String created_by;
    private Timestamp generated_at;
    private boolean is_public=false;
    private boolean is_deleted=false;
    private double grondslag = 8.0;
    private Integer autorisatieniveau = 1;
    private Integer afhandelcode = 11;
    private List<NoteTranscript> transcripts;
    private List<Multimedia> multimedia;
    private List<SharedNote> shareDetails;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id= id;
    }
    public List<Multimedia> getMultimedia() {
        return multimedia;
    }
    public void setMultimedia(List<Multimedia> multimedia) {
        this.multimedia = multimedia;
    }
    public List<NoteTranscript> getTranscripts() {
        return transcripts;
    }
    public void setTranscripts(List<NoteTranscript> transcript) {
        this.transcripts = transcript;
    }
    public List<SharedNote> getShareDetails() {
        return shareDetails;
    }
    public void setShareDetails(List<SharedNote> shareDetails) {
        this.shareDetails = shareDetails;
    }
    public UUID getNoteID() {
        return noteID;
    }
    public void setNoteID(UUID noteID) {
        this.noteID = noteID;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getNote_text() {
        return note_text;
    }
    public void setNote_text(String note_text) {
        this.note_text = note_text;
    }
    public Integer getVersion() {
        return version;
    }
    public void setVersion(Integer version) {
        this.version = version;
    }
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public String getCreated_by() {
        return created_by;
    }
    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }
    public Timestamp getGenerated_at() {
        return generated_at;
    }
    public void setGenerated_at(Timestamp generated_at) {
        this.generated_at = generated_at;
    }
    public boolean isIs_public() {
        return is_public;
    }
    public void setIs_public(boolean is_public) {
        this.is_public = is_public;
    }
    public boolean isIs_deleted() {
        return is_deleted;
    }
    public void setIs_deleted(boolean is_deleted) {
        this.is_deleted = is_deleted;
    }
    public double getGrondslag() {
        return grondslag;
    }
    public void setGrondslag(double grondslag) {
        this.grondslag = grondslag;
    }
    public Integer getAutorisatieniveau() {
        return autorisatieniveau;
    }
    public void setAutorisatieniveau(Integer autorisatieniveau) {
        this.autorisatieniveau = autorisatieniveau;
    }
    public Integer getAfhandelcode() {
        return afhandelcode;
    }
    public void setAfhandelcode(Integer afhandelcode) {
        this.afhandelcode = afhandelcode;
    }

    @Override
    public int compareTo(Note note) {
    	return note.getGenerated_at().compareTo(getGenerated_at());
    }
}
