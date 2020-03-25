package nl.politie.predev.android.zakboek;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public class Multimedia {

	private Long id;

	private UUID multimediaID;
	private String title;
	private String filepath;
	private String filetype;
	private UUID noteID;
	private Integer noteVersion;
	private boolean isDeleted;
	private String content;
	@JsonIgnore
	private String localFilePath;

	public boolean isDeleted(){
		return isDeleted;
	}
	public void setDeleted(boolean deleted){
		this.isDeleted = deleted;
	}
	public Integer getNoteVersion() {
		return noteVersion;
	}
	public void setNoteVersion(Integer noteVersion) {
		this.noteVersion = noteVersion;
	}
	public UUID getNoteID() {
		return noteID;
	}
	public void setNoteID(UUID noteUUID) {
		this.noteID = noteUUID;
	}
	public UUID getMultimediaID() {
		return multimediaID;
	}
	public void setMultimediaID(UUID multimediaID) {
		this.multimediaID = multimediaID;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getFilepath() {
		return filepath;
	}
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
	public String getFiletype() {
		return filetype;
	}
	public void setFiletype(String filetype) {
		this.filetype = filetype;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	@JsonIgnore
	public String getLocalFilePath() {
		return localFilePath;
	}
	@JsonIgnore
	public void setLocalFilePath(String localFilePath) {
		this.localFilePath = localFilePath;
	}
}
