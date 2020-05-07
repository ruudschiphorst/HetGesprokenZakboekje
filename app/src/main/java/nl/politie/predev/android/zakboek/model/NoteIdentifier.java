package nl.politie.predev.android.zakboek.model;

import java.util.UUID;

public class NoteIdentifier {

	private UUID noteID;
	private Integer version;

	public Integer getVersion() {
		return version;
	}
	public void setVersion(Integer version) {
		this.version = version;
	}
	public UUID getNoteID() {
		return noteID;
	}
	public void setNoteID(UUID noteID) {
		this.noteID = noteID;
	}
}
