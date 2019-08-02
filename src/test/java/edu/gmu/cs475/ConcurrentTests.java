package edu.gmu.cs475;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import edu.gmu.cs475.internal.Command;
import edu.gmu.cs475.struct.ITag;
import edu.gmu.cs475.internal.DeadlockDetectorAndRerunRule;

public class ConcurrentTests {
	/* Leave at 6 please */
	public static final int N_THREADS = 6;

	@Rule
	public DeadlockDetectorAndRerunRule timeout = new DeadlockDetectorAndRerunRule(10000);

	/**
	 * Use this instance of fileManager in each of your tests - it will be
	 * created fresh for each test.
	 */
	AbstractFileTagManager fileManager;

	/**
	 * Automatically called before each test, initializes the fileManager
	 * instance
	 */
	@Before
	public void setup() throws IOException {
		fileManager = new FileTagManager();
		fileManager.init(Command.listAllFiles());
	}

	/**
	 * Create N_THREADS threads, with half of the threads adding new tags and
	 * half iterating over the list of tags and counting the total number of
	 * tags. Each thread should do its work (additions or iterations) 1,000
	 * times. Assert that the additions all succeed and that the counting
	 * doesn't throw any ConcurrentModificationException. There is no need to
	 * make any assertions on the number of tags in each step.
	 */
	@Test
	public void testP1AddAndListTag() throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
		int i;
		for(i = 0;  i < N_THREADS/2; i++){// 3 threads

			executor.submit(() -> {// create 1000 tags
				try {
					List<Integer> numList = new ArrayList<Integer>(1000);
					for (int j = 0; j < 1000; j++)
						numList.add(j);
					int tagCount = 0;
					while(tagCount < 1000){
						fileManager.addTag(String.valueOf(numList.get(tagCount)));
						tagCount++;
					}

				} catch (Exception e) {
					fail("Should not have any exceptions adding tags");
				}

			});
		}
		for(i = 0;  i < N_THREADS/2; i++){// count number of tags

			executor.submit(() -> {// create 1000 tags randomly
				try {
					int count = 0;
					Iterable<? extends ITag> tagList = fileManager.listTags();
					for( ITag tag : tagList){
						count++;
					}

				} catch (ConcurrentModificationException e) {
					fail("Should not have any concurrent exceptions");
				}
			});
		}

		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);
		assert (true);//if no exceptions were thrown everything succeeded
	}

	/**
	 * Create N_THREADS threads, and have each thread add 1,000 different tags;
	 * assert that each thread creating a different tag succeeds, and that at
	 * the end, the list of tags contains all of tags that should exist
	 */
	@Test
	public void testP1ConcurrentAddTagDifferentTags() throws InterruptedException, ExecutionException {


		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);

		class TagGenerator implements Callable {//for creating N number for unique tags
			int part;

			TagGenerator(int p) {
				part = p;
			}

			@Override
			public Object call() throws Exception {
				try {
					System.out.println("in thread: " + part);
					int start = part * 1000;
					int end = start + 1000;
					List<Integer> numList = new ArrayList<Integer>(1000);
					for (int j = start; j < end; j++)
						numList.add(j);
					int tagCount = 0;
					while (tagCount < 1000) {
						fileManager.addTag(String.valueOf(numList.get(tagCount)));
						tagCount++;
					}
					System.out.println("thread " + part + " finished.");

				} catch (Exception e) {
					fail("Should not have any exceptions adding tags");

				}
				return null;
			}
		}

		List<Callable<TagGenerator>> taskList = new ArrayList<>();

		for(int i = 0; i< N_THREADS; i++){
			taskList.add(new TagGenerator(i));
		}
		List<Future<TagGenerator>> futures = executor.invokeAll(taskList);
		System.out.println(futures.size());
		for (Future future : futures){
			System.out.println(future.get());
		}

		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);
		int count = 0;
		Iterable<? extends ITag> tagList = fileManager.listTags();
		for( ITag tag : tagList){//count tags
			count++;
		}
		System.out.println("count is: " + count);
		assert (count == 6001);//6 threads making 1000 should create 6000 tags
	}

	/**
	 * Create N_THREADS threads. Each thread should try to add the same 1,000
	 * tags of your choice. Assert that each unique tag is added exactly once
	 * (there will be N_THREADS attempts to add each tag). At the end, assert
	 * that all tags that you created exist by iterating over all tags returned
	 * by listTags()
	 */
	@Test
	public void testP1ConcurrentAddTagSameTags() {

	}

	/**
	 * Create 1000 tags. Save the number of files (returned by listFiles()) to a
	 * local variable.
	 * 
	 * Then create N_THREADS threads. Each thread should iterate over all files
	 * (from listFiles()). For each file, it should select a tag and random from
	 * the list returned by listTags(). Then, it should tag that file with that
	 * tag. Then (regardless of the tagging sucedding or not), it should pick
	 * another random tag, and delete it. You do not need to care if the
	 * deletions pass or not either.
	 * 
	 * 
	 * At the end (once all threads are completed) you should check that the
	 * total number of files reported by listFiles matches what it was at the
	 * beginning. Then, you should list all of the tags, and all of the files
	 * that have each tag, making sure that the total number of files reported
	 * this way also matches the starting count. Finally, check that the total
	 * number of tags on all of those files matches the count returned by
	 * listTags.
	 * 
	 */
	@Test
	public void testP2ConcurrentDeleteTagTagFile() throws Exception {

	}

	/**
	 * Create a tag. Add each tag to every file. Then, create N_THREADS and have
	 * each thread iterate over all of the files returned by listFiles(),
	 * calling removeTag on each to remove that newly created tag from each.
	 * Assert that each removeTag succeeds exactly once.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testP2RemoveTagWhileRemovingTags() throws Exception {

	}

	/**
	 * Create N_THREADS threads and N_THREADS/2 tags. Half of the threads will
	 * attempt to tag every file with (a different) tag. The other half of the
	 * threads will count the number of files currently having each of those
	 * N_THREADS/2 tags. Assert that there all operations succeed, and that
	 * there are no ConcurrentModificationExceptions. Do not worry about how
	 * many files there are of each tag at each step (no need to assert on
	 * this).
	 */
	@Test
	public void testP3ConcurrentEchoAll() throws Exception {

		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);

		class EchoTest implements Callable {//for echoing on each thread

			@Override
			public Object call() throws Exception {//echos random ints to aall files
				try {
					Random rand = new Random();
					int num = rand.nextInt();
					System.out.println("thread started");
					fileManager.echoToAllFiles("untagged", String.valueOf(num));
				} catch (Exception e) {
					fail("Should not have any exceptions");
				}
				System.out.println("thread ffinished");
				return null;
			}
		}

		List<Callable<EchoTest>> taskList = new ArrayList<>();

		for(int i = 0; i< N_THREADS; i++){
			taskList.add(new EchoTest());
		}
		List<Future<EchoTest>> futures = executor.invokeAll(taskList);
		System.out.println(futures.size());
		for (Future future : futures){
			System.out.println(future.get());
		}

		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);
		String testString = "";
		Iterable<? extends TaggedFile> fileList = (Iterable<? extends TaggedFile>) fileManager.listFilesByTag("untagged");
		String compareString = fileManager.readFile(fileList.iterator().next().getName());
		for(TaggedFile testFile : fileList) {
			testString = fileManager.readFile(testFile.getName());
			System.out.println(testString + " " + compareString);
			assert (testString.equals(compareString));
		}
	}

	/**
	 * Create N_THREADS threads, and have half of those threads try to echo some
	 * text into all of the files. The other half should try to cat all of the
	 * files, asserting that all of the files should always have the same
	 * content.
	 */
	@Test
	public void testP3EchoAllAndCatAll() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);

		class EchoTest implements Callable {//for echoing on threads

			@Override
			public Object call() throws Exception {//echos random ints to all files
				try {
					Random rand = new Random();
					int num = rand.nextInt();
					System.out.println("thread started");
					fileManager.echoToAllFiles("untagged", String.valueOf(num));
				} catch (Exception e) {
					fail("Should not have any exceptions");
				}
				System.out.println("thread ffinished");
				return null;
			}
		}

		class CatTest implements Callable {//for catall on threads

			@Override
			public Object call() throws Exception {//cat files then assert all files are the same text
				try {
					System.out.println("thread started");
					fileManager.catAllFiles("untagged");
					String testString = "";
					Iterable<? extends TaggedFile> fileList = (Iterable<? extends TaggedFile>) fileManager.listFilesByTag("untagged");
					String compareString = fileManager.readFile(fileList.iterator().next().getName());
					for(TaggedFile testFile : fileList) {
						testString = fileManager.readFile(testFile.getName());
						System.out.println(testString + " " + compareString);
						assert (testString.equals(compareString));
					}
				} catch (Exception e) {
					fail("Should not have any exceptions");
				}
				System.out.println("thread finished");
				return null;
			}
		}

		for(int i = 0; i< N_THREADS/2; i++){//3 threads of echo
			executor.submit(new EchoTest());
		}
		for(int i = 0; i< N_THREADS/2; i++){//3 threads for cat and assertion
			executor.submit(new CatTest());
		}

		executor.shutdown();
		executor.awaitTermination(30, TimeUnit.SECONDS);

	}
}
