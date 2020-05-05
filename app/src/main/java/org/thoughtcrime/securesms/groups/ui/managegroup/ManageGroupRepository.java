package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupAccessControl;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.MembershipNotSuitableForV2Exception;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class ManageGroupRepository {

  private static final String TAG = Log.tag(ManageGroupRepository.class);

  private final Context         context;
  private final GroupId.Push    groupId;
  private final ExecutorService executor;

  ManageGroupRepository(@NonNull Context context, @NonNull GroupId.Push groupId) {
    this.context  = context;
    this.executor = SignalExecutors.BOUNDED;
    this.groupId  = groupId;
  }

  public GroupId getGroupId() {
    return groupId;
  }

  void getGroupState(@NonNull Consumer<GroupStateResult> onGroupStateLoaded) {
    executor.execute(() -> onGroupStateLoaded.accept(getGroupState()));
  }

  @WorkerThread
  private GroupStateResult getGroupState() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient      groupRecipient = Recipient.externalGroup(context, groupId);
    long           threadId       = threadDatabase.getThreadIdFor(groupRecipient);

    return new GroupStateResult(threadId, groupRecipient);
  }

  void setExpiration(int newExpirationTime, @NonNull ErrorCallback error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.updateGroupTimer(context, groupId.requirePush(), newExpirationTime);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NOT_A_MEMBER);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  void applyMembershipRightsChange(@NonNull GroupAccessControl newRights, @NonNull ErrorCallback error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.applyMembershipAdditionRightsChange(context, groupId.requireV2(), newRights);
      } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  void applyAttributesRightsChange(@NonNull GroupAccessControl newRights, @NonNull ErrorCallback error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.applyAttributesRightsChange(context, groupId.requireV2(), newRights);
      } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      }
    });
  }

  public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
    SimpleTask.run(SignalExecutors.BOUNDED,
                   () -> Recipient.externalGroup(context, groupId),
                   recipientCallback::accept);
  }

  void setMuteUntil(long until) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientId recipientId = Recipient.externalGroup(context, groupId).getId();
      DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until);
    });
  }

  void addMembers(@NonNull List<RecipientId> selected, @NonNull ErrorCallback error) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.addMembers(context, groupId, selected);
      } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NO_RIGHTS);
      } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
        Log.w(TAG, e);
        error.onError(FailureReason.OTHER);
      } catch (MembershipNotSuitableForV2Exception e) {
        Log.w(TAG, e);
        error.onError(FailureReason.NOT_CAPABLE);
      }
    });
  }

  static final class GroupStateResult {

    private final long      threadId;
    private final Recipient recipient;

    private GroupStateResult(long threadId,
                             Recipient recipient)
    {
      this.threadId  = threadId;
      this.recipient = recipient;
    }

    long getThreadId() {
      return threadId;
    }

    Recipient getRecipient() {
      return recipient;
    }
  }

}
