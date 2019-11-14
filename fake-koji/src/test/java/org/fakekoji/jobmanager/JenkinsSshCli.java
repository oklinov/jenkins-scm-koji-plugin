/*
 * The MIT License
 *
 * Copyright 2017 jvanek.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.fakekoji.jobmanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.fakekoji.Utils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Warning - reaming check have missing check on content!
 *
 * @author jvanek
 */
public class JenkinsSshCli {

    /**
     * To run this test, you have to have instance of jenkins running, and have
     * ssh connection on JenkinsCliWrapper.host and JenkinsCliWrapper.port open.
     * To pass out of such a network, this is set in beforeclass and assumed in
     * each test
     *
     */
    private static boolean haveJenkinsWithSsh;
    //some test assume jobs dir is known (eg those who test work on forcibly removed jobs)
    private static File workdir = new File("/home/jvanek/Desktop/jenkins/jenkins_home/jobs");

    private static final String sure_job = "super_sure_jsc_test_job";

    @BeforeClass
    public static void beforeClass() throws Exception {
        try {
            JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().help();
            haveJenkinsWithSsh = (r.res == 0 && r.ex == null);
            if (r.ex != null) {
                r.ex.printStackTrace();
            }
        } catch (Exception e) {
            haveJenkinsWithSsh = false;
        }
        if (haveJenkinsWithSsh) {
            //ensure jenkins have at least one job
            JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().createJob(sure_job, getInternalTestJobConfig());
            Assert.assertEquals(0, r.res);
            Assert.assertNull(r.ex);
        }

    }

    @AfterClass
    public static void afterClass() {
        if (haveJenkinsWithSsh) {
            JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().deleteJobs(sure_job);
            Assert.assertEquals(0, r.res);
            Assert.assertNull(r.ex);
        }
    }

    private static String inputStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());

    }

    private static InputStream stringToInputStream(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(b);
    }

    @Test
    /**
     * Indeed, there was a code in the JenkinsCliWrapper.syncSshExec which was
     * able to break this test by leaving strange semi opened connections
     * (intentionally not saying not closed) when quick sequence of jobs ssh
     * conns was started
     */
    public void testHelpStress() throws IOException, InterruptedException {
        Assume.assumeTrue(haveJenkinsWithSsh);
        for (int x = 1; x <= 100; x++) {
            JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().help();
            Assert.assertEquals(0, r.res);
            Assert.assertTrue(r.so.isEmpty());
            Assert.assertFalse(r.se.isEmpty());
            Assert.assertTrue(r.se.length() > 100);
            Assert.assertNull(r.ex);
        }
    }

    @Test
    public void testListJobs() throws Throwable {
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().listJobs();
        Assert.assertEquals(0, r.res);
        Assert.assertFalse(r.so.isEmpty());
        Assert.assertTrue(r.se.isEmpty());
        Assert.assertNull(r.ex);
        String[] jobs = JenkinsCliWrapper.getCli().listJobsToArray();
        Assert.assertNotNull(jobs);
        Assert.assertNotNull(jobs.length >= 1);//at least our sure_job is here
        Arrays.sort(jobs);
        int i = Arrays.binarySearch(jobs, sure_job);
        Assert.assertTrue(i >= 0);
    }

    @Test
    public void testcreateDeleteJob() throws IOException, InterruptedException {
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r1 = JenkinsCliWrapper.getCli().createJob("first_test_java_job1", getInternalTestJobConfig());
        Assert.assertEquals(0, r1.res);
        Assert.assertNull(r1.ex);
        JenkinsCliWrapper.ClientResponse r2 = JenkinsCliWrapper.getCli().createJob("first_test_java_job2", getInternalTestJobConfig());
        Assert.assertEquals(0, r2.res);
        Assert.assertNull(r2.ex);
        JenkinsCliWrapper.ClientResponse r3 = JenkinsCliWrapper.getCli().createJob("first_test_java_job3", getInternalTestJobConfig());
        Assert.assertEquals(0, r3.res);
        Assert.assertNull(r3.ex);
        JenkinsCliWrapper.ClientResponse r33 = JenkinsCliWrapper.getCli().createJob("first_test_java_job3", getInternalTestJobConfig());
        Assert.assertEquals(4, r33.res); //can not override like this, have separate method
        Assert.assertNull(r33.ex);
        JenkinsCliWrapper.ClientResponse r4 = JenkinsCliWrapper.getCli().deleteJobs("first_test_java_job1");
        Assert.assertEquals(0, r4.res);
        Assert.assertNull(r4.ex);
        JenkinsCliWrapper.ClientResponse r5 = JenkinsCliWrapper.getCli().deleteJobs("first_test_java_job2", "first_test_java_job3");
        Assert.assertEquals(0, r5.res);
        Assert.assertNull(r5.ex);
    }

    private static final String TESTING_JOB_SRC = "org/fakekoji/jobmanager/simple_job";

    private static InputStream getInternalTestJobConfig() {
        return ClassLoader.getSystemClassLoader().getResourceAsStream(TESTING_JOB_SRC);
    }

    @Test
    public void testReloadAll() throws Throwable {
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().relaodAll();
        Assert.assertEquals(0, r.res);
        Assert.assertTrue(r.so.isEmpty());
        Assert.assertTrue(r.se.isEmpty());
        Assert.assertNull(r.ex);
    }

    @Test
    public void testUpdateNotExistingJob() throws Throwable {
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r = JenkinsCliWrapper.getCli().updateJob("definitly_not_existing_job", null /*ok while it really do not exists*/);
        Assert.assertNotEquals(0, r.res);
        Assert.assertTrue(r.so.isEmpty());
        Assert.assertFalse(r.se.isEmpty());
        Assert.assertNull(r.ex);
    }

    @Test
    public void testUpdateExistingJob() throws Throwable {
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r1 = JenkinsCliWrapper.getCli().getJob(sure_job);
        Assert.assertEquals(0, r1.res);
        Assert.assertFalse(r1.so.isEmpty());
        Assert.assertTrue(r1.se.isEmpty());
        Assert.assertNull(r1.ex);
        JenkinsCliWrapper.ClientResponse r2 = JenkinsCliWrapper.getCli().updateJob(sure_job, stringToInputStream(r1.so.replace("by java test", "by test java")));
        Assert.assertEquals(0, r2.res);
        Assert.assertTrue(r2.so.isEmpty());
        Assert.assertTrue(r2.se.isEmpty());
        Assert.assertNull(r2.ex);
        Assume.assumeTrue(haveJenkinsWithSsh);
        JenkinsCliWrapper.ClientResponse r3 = JenkinsCliWrapper.getCli().getJob(sure_job);
        Assert.assertEquals(0, r3.res);
        Assert.assertFalse(r3.so.isEmpty());
        Assert.assertTrue(r3.se.isEmpty());
        Assert.assertNull(r3.ex);
        Assert.assertNotEquals(r1.so, r3.so);
    }

    @Test
    public void manuallyOperateConfigXml() throws Throwable {
        Assume.assumeTrue(haveJenkinsWithSsh);
        Assume.assumeTrue(workdir.exists());
        final String manualJobName = "manual_java_test_auto_job";
        try {
            //manually reate config.xml
            File manualJob = new File(workdir, manualJobName);
            manualJob.deleteOnExit();
            manualJob.mkdir();
            File config = new File(manualJob, "config.xml");
            Utils.writeToFile(config, inputStreamToString(getInternalTestJobConfig()));

            //verify it do not exists in jenkin
            JenkinsCliWrapper.ClientResponse r1 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertNotEquals(0, r1.res);
            Assert.assertTrue(r1.so.isEmpty());
            Assert.assertFalse(r1.se.isEmpty());
            Assert.assertTrue(r1.se.contains("No such job"));
            Assert.assertNull(r1.ex);
            String[] jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            int i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i < 0);

            //notify jenkins
            JenkinsCliWrapper.ClientResponse r2 = JenkinsCliWrapper.getCli().reloadOrRegisterManuallyUploadedJob(workdir, manualJobName);
            Assert.assertEquals(0, r2.res);
            Assert.assertNull(r2.ex);

            //verify it do exists
            JenkinsCliWrapper.ClientResponse r3 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertEquals(0, r3.res);
            Assert.assertFalse(r3.so.isEmpty());
            Assert.assertTrue(r3.se.isEmpty());
            Assert.assertNull(r3.ex);
            jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i >= 0);

            //build it few times
            for (int x = 1; x <= 3; x++) {
                JenkinsCliWrapper.ClientResponse r4 = JenkinsCliWrapper.getCli().buildAndWait(manualJobName);
                Assert.assertEquals(0, r4.res);
                Assert.assertNull(r4.ex);
            }
            File archive = new File(manualJob, "builds");
            String[] builds = archive.list();
            Arrays.sort(builds);
            for (int x = 1; x <= 3; x++) {
                i = Arrays.binarySearch(builds, "" + x);
                Assert.assertTrue(i >= 0);
            }
            //mv it out, can not to temp, as it is different volume, Files.move would fail
            File trash = new File(workdir.getParent(), "trash");
            trash.deleteOnExit();
            Utils.moveFile(manualJob, trash);
            //although the fille is not here
            Assert.assertFalse(manualJob.exists());
            //and is not readable
            r3 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertNotEquals(0, r3.res);
            Assert.assertTrue(r3.so.isEmpty());
            Assert.assertFalse(r3.se.isEmpty());
            Assert.assertNull(r3.ex);
            //highlight it is still registered
            jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i >= 0);

            //deregister it
            JenkinsCliWrapper.ClientResponse r4 = JenkinsCliWrapper.getCli().deleteJobs(manualJobName);
            Assert.assertEquals(0, r4.res);
            Assert.assertNull(r4.ex);
            //ensure it is gone
            r3 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertNotEquals(0, r3.res);
            Assert.assertTrue(r3.so.isEmpty());
            Assert.assertFalse(r3.se.isEmpty());
            Assert.assertNull(r3.ex);
            jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i < 0);
            //mv it back
            Utils.moveFile(trash, manualJob);
            //ensure it is not here
            r3 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertNotEquals(0, r3.res);
            Assert.assertTrue(r3.so.isEmpty());
            Assert.assertFalse(r3.se.isEmpty());
            Assert.assertNull(r3.ex);
            jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i < 0);
            //register it
            r2 = JenkinsCliWrapper.getCli().reloadOrRegisterManuallyUploadedJob(workdir, manualJobName);
            Assert.assertEquals(0, r2.res);
            Assert.assertNull(r2.ex);
            //ensure it is really here
            r3 = JenkinsCliWrapper.getCli().getJob(manualJobName);
            Assert.assertEquals(0, r3.res);
            Assert.assertFalse(r3.so.isEmpty());
            Assert.assertTrue(r3.se.isEmpty());
            Assert.assertNull(r3.ex);
            jobs = JenkinsCliWrapper.getCli().listJobsToArray();
            Arrays.sort(jobs);
            i = Arrays.binarySearch(jobs, manualJobName);
            Assert.assertTrue(i >= 0);

            //build it few times
            for (int x = 1; x <= 3; x++) {
                r4 = JenkinsCliWrapper.getCli().buildAndWait(manualJobName);
                Assert.assertEquals(0, r4.res);
                Assert.assertNull(r4.ex);
            }
            //verify also previous jobs persisted
            archive = new File(manualJob, "builds");
            builds = archive.list();
            Arrays.sort(builds);
            for (int x = 1; x <= 6; x++) {
                i = Arrays.binarySearch(builds, "" + x);
                Assert.assertTrue(i >= 0);
            }
        } finally {
            JenkinsCliWrapper.getCli().deleteJobs(manualJobName);
            //not nesurring sucess, hard to tell when we failed
        }

    }

}
