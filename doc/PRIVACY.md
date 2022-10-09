# Privacy Policy

In the following, "Instant Poll" refers to the Discord application "Instant Poll#9956", created by me, [Johnny](https://github.com/JohnnyJayJay).
"Server" or "guild" means "Discord server".
This privacy policy lays out

- what data Instant Poll collects and stores
- what that data is used for
- how to request or delete your data (if applicable)

## Where data isn't collected

As a preface, let's clear up a few common scenarios where no data is collected at all:

- When running `/poll info` or `/poll help`

- When displaying the results of a poll using the "Show Results" button

- When you do anything at all that does not directly interact with Instant Poll
  (meaning: Instant Poll doesn't read your messages or other activity)

## Collected Data

Instant Poll collects and stores data related to the polls it manages. This includes:

- User ID of the Discord user who created the poll

- User IDs of every Discord user who participated in the poll, associated with the option(s) they picked

- The message ID of the `/poll create` command execution

- The information directly passed through the arguments to the `/poll create` command, including the poll question and the answer texts

The purpose of storing all of these data points is simply to provide the basic functionality Instant Poll offers. Without storing this data, it is not possible for Instant Poll to function. Storing the creator's user ID is required to check whether a user is allowed to close or edit a poll. Storing the user IDs of participants as well as their selected options is required to display the results of a poll and count the votes. The message ID of the command execution is used to identify polls. Finally, the information directly passed to Instant Poll directly impacts poll display and additional features such as timed polls or whether the results should be anonymous or not. To provide all of these features, this information must be stored.

**Why is it stored persistently?**

The data is not stored only temporarily, but persistently, meaning it is stored on a physical drive and persisted across restarts. This is done so that, even if Instant Poll is restarted, previously created polls can still be accessed and interacted with.

## How Your Data Is Processed

Your data is solely processed for the core functionality of Instant Poll. Your data is not shared with any third party, used for analytics, advertisement or similar.

## Your Data Rights

Under the EU's General Data Protection Rule (GDPR), you have the right to request insight into the data that is associated with you or request its permanent deletion. 

"Data that is associated with you" includes the following (exhaustive):

- Polls created by you

- Your participation records in polls created by other users

To perform any of the actions described in the following, you need to have access to a Discord server with Instant Poll on it.

### How to request your data

Instant Poll allows you to regularly request a download link to all data that can be associated with you. 

To do so, perform the following steps:

1. Run `/poll data request`

2. Now, you can do one of two things:
   
   - Wait for the request to complete and receive your data archive as a downloadable file immediately.
   
   - The request might take a while; you can also check back later, using the same command, to receive your data package once it has successfully been collected.

3. Download your data file via the Discord download functionality. The file should be a zip archive.

4. The downloaded file contains all the data that is associated with you.

### How to delete your data

Instant Poll allows you to regularly delete all data that can be associated with you.

To do so, perform the following steps:

1. Run `/poll data delete`

2. The deletion of your data has been queued and will be performed automatically. No further action is required.

Note that as soon as you interact with polls created through Instant Poll again, new data that can be associated with you will be stored! Additionally, it is generally *not* possible for Instant Poll to delete poll messages in Discord, even when the poll associated with them is deleted from Instant Poll's database.

## Contact

If you have any questions or concerns regarding the contents of this document, feel free to reach out via:

- Discord: Johnny#3826

- Email: johnnyjayjay02(at)gmail.com
