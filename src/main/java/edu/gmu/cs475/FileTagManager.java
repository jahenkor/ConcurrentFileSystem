package edu.gmu.cs475;

import static edu.gmu.cs475.AbstractFileTagManager.BASEDIR;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.struct.ITaggedFile;
import edu.gmu.cs475.struct.NoSuchTagException;
import edu.gmu.cs475.struct.TagExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;


/*
* Julius Ahenkora G00835346
* John Hunt 01030586
*/
public class FileTagManager extends AbstractFileTagManager {

	List<Tag> tags = new ArrayList<>();
	List<TaggedFile> taggedFiles = new ArrayList<>();// list of tagged files
        private final StampedLock lock = new StampedLock();
        private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        private final Lock readLock = readWriteLock.readLock();
        private final Lock writeLock = readWriteLock.writeLock();


    @Override
    public Iterable<? extends ITag> listTags() {
        long stamp = lock.tryOptimisticRead();
        List<Tag> tagList = tags;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                tagList = tags;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return tagList;
    }

    @Override
    public ITag addTag(String name) throws TagExistsException {

        Tag temp = new Tag(name);
        long stamp = lock.readLock();
        try{
            if(tagExists(name)){throw new TagExistsException();}
            long ws = lock.tryConvertToWriteLock(stamp);
            if (ws != 0L) {
                stamp = ws;
                tags.add(temp);
            }
            else{
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                tags.add(temp);
            }
        }finally {
            lock.unlock(stamp);
        }

        return temp;
    }

    @Override
    public ITag editTag(String oldTagName, String newTagName) throws TagExistsException, NoSuchTagException {
        Tag oldTag = null;
        long stamp = lock.readLock();
        try {
            if (tagExists(newTagName)) {
                throw new TagExistsException();
            }

            oldTag = findTag(oldTagName);

            if (oldTag == null) {
                throw new NoSuchTagException();
            }
            long ws = lock.tryConvertToWriteLock(stamp);
            if (ws != 0L) {
                stamp = ws;
                oldTag.setName(newTagName);
            }
            else{
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                oldTag.setName(newTagName);
            }
        }finally {
            lock.unlock(stamp);
        }
        return oldTag;
    }

    @Override
    public ITag deleteTag(String tagName) throws NoSuchTagException, DirectoryNotEmptyException {
        long stamp = lock.readLock();
        try{
            for(Tag x : tags){//search for tag match param
                if(x.getName().equals(tagName)){
                    if(x.hasFiles()){//if the tag has files attached to it throw erro
                        throw new DirectoryNotEmptyException(x.getName());
                    }
                    else {//otherwise complete the deletion
                        long ws = lock.tryConvertToWriteLock(stamp);
                        if (ws != 0L) {
                            stamp = ws;
                            tags.remove(x);
                            return x;
                        }
                        else{
                            lock.unlockRead(stamp);
                            stamp = lock.writeLock();
                            tags.remove(x);
                            return x;
                        }
                    }
                }
            }
        }finally {
            lock.unlock(stamp);
        }

        throw new NoSuchTagException();
    }

	@Override
    public void init(List<Path> files) {
        TaggedFile currentFile = null;
        Tag unTagged = null;//create untagged Tag

        unTagged = new Tag("untagged");//call add tag the Tag object is returned as iTag and cast to Tag
        tags.add(unTagged);

        for (Path file : files) {//iterate thru files
            currentFile = new TaggedFile(file, unTagged);//each files is a new object with untagged as its first tag
            taggedFiles.add(currentFile);//add to our files list
            unTagged.files.add(currentFile);//add file to untagged file list
            
        }
    }


    @Override
    public Iterable<? extends TaggedFile> listAllFiles() {//simply return the list of files
        long stamp = lock.tryOptimisticRead();
        List<TaggedFile> fileList = taggedFiles;
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                fileList = taggedFiles;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return fileList;
    }

    @Override
    public Iterable<? extends TaggedFile> listFilesByTag(String tag) throws NoSuchTagException {
        long stamp = lock.tryOptimisticRead();
        for(Tag x : tags) {//search for tag match param
            if (x.getName().equals(tag)) {
                return x.files;//return list of files attached to tage
            }
        }
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                for(Tag x : tags) {//search for tag match param
                    if (x.getName().equals(tag)) {
                        return x.files;//return list of files attached to tage
                    }
                }
            } finally {
                lock.unlockRead(stamp);
            }
        }
        throw new NoSuchTagException();//tag param wasnt found
    }

    @Override
    public boolean tagFile(String file, String tag) throws NoSuchFileException, NoSuchTagException{

        long stamp = lock.writeLock();
        try {

            Tag tagObj = null;
            TaggedFile fileObj = null;

            if (tag.equals("untagged")) {
                return false;
            } //Cannot use untagged as tag

            if (!tagExists(tag)) {
                throw new NoSuchTagException();
            } //No tag found

            tagObj = findTag(tag);


            if (!fileExists(file)) {
                throw new NoSuchFileException(file);
            } //File not found in untagged files


            fileObj = findFile(file);

            if (fileExists(file) && tagObj.files.contains(fileObj)) {
                return false;
            } //File already has tag

            fileObj.tags.remove(findTag("untagged")); //Remove untagged from file tag
            findTag("untagged").files.remove(fileObj);
            taggedFiles.get(taggedFiles.indexOf(fileObj)).tags.remove(findTag("untagged"));

            fileObj.tags.add(tagObj);

            tagObj.files.add(fileObj);
            return true;
        }finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeTag(String file, String tag) throws NoSuchFileException, NoSuchTagException {

        long stamp = lock.writeLock();
        try {
            if (tag.equals("untagged")) {
                return false;
            }

            Tag tagObj = null;
            TaggedFile fileObj = null;


            tagObj = findTag(tag);


            if (tagObj == null) {
                throw new NoSuchTagException();
            } //No tag found

            fileObj = findFile(file);

            if (fileObj == null) {
                throw new NoSuchFileException(file);
            } // No file found


            tagObj.files.remove(fileObj); //Remove file from tag list
            fileObj.tags.remove(tagObj); //Remove tag from file tag list

            if (fileObj.tags.isEmpty()) {
                fileObj.tags.add(findTag("untagged"));
            } //Add untagged to file if tag lists empty
            return true;
        }finally {
            lock.unlockWrite(stamp);
        }
    }

	@Override
	public Iterable<? extends ITag> getTags(String file) throws NoSuchFileException {

        long stamp = lock.tryOptimisticRead();

        TaggedFile fileObj = findFile(file);
            
        if(fileObj == null){throw new NoSuchFileException(file);}

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                fileObj = findFile(file);
            } finally {
                lock.unlockRead(stamp);
            }
        }

		return fileObj.tags;  
	}

    @Override
    public String catAllFiles(String tag) throws NoSuchTagException, IOException {
        String catFiles = "";
        Iterable<? extends TaggedFile> fileList = listFilesByTag(tag);
        Long stamp = lockFile(tag, false);
        try{
            if(!tagExists(tag)){throw new NoSuchTagException();}

            //Find file content according to tag, and store in string
            for(TaggedFile tagFile : fileList){
                catFiles += FileTagManager.super.readFile(tagFile.getName());
            }
        }finally {
            unLockFile(tag, stamp, false);
        }

        return catFiles;
    }

    @Override
    public void echoToAllFiles(String tag, String content) throws NoSuchTagException, IOException {
        Iterable<? extends TaggedFile> fileList = listFilesByTag(tag);
        Long stamp = lockFile(tag, false);
        try{
            if(!tagExists(tag)){throw new NoSuchTagException();}

            unLockFile(tag, stamp, false);
            stamp = lockFile(tag, true);
            for(TaggedFile tagFile : fileList){
                FileTagManager.super.writeFile(tagFile.getName(), content);
            }
        }finally {
            unLockFile(tag, stamp, true);
        }


    }

    @Override
    public long lockFile(String name, boolean forWrite) throws NoSuchFileException {
        long stamp = 0;
        if(forWrite){
            stamp = lock.writeLock();
        }
        else {
            stamp = lock.readLock();
        }
        return stamp;
    }

    @Override
    public void unLockFile(String name, long stamp, boolean forWrite) throws NoSuchFileException {
        if(forWrite){
            lock.unlockWrite(stamp);
        }
        else{
            lock.unlockRead(stamp);
        }
    }
        
        /**
         * 
         * @param tag
         * @return Tag found in list of tags
         */
        public Tag findTag(String tag){
            
            for(Tag tagObj : tags){ //Find tag
                if(tagObj.getName().equals(tag)){
                    return tagObj;
                }
            }
            
            return null;
        }
        
        public boolean tagExists(String tag){
            
            for(Tag tagObj : tags){ //Find tag
                if(tagObj.getName().equals(tag)){
                    return true;
                }
            }
            
            return false;
        }
        /**
         * 
         * @param file
         * @return File found in list of taggedfiles
         */
        public TaggedFile findFile(String file){
            
            for(TaggedFile fileObj : taggedFiles){ //Find file from list of tagged files
                if(fileObj.getName().equals(file)){
                    return fileObj;
                }
            }
            
            return null;
            
        }

        public boolean fileExists(String file){
            
            for(TaggedFile fileObj : taggedFiles){ //Find file from list of tagged files
                if(fileObj.getName().equals(file)){
                    return true;
                }
            }
            
            return false;
            
        }
}
