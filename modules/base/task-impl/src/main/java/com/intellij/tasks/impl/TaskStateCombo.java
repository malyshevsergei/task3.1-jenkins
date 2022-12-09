package com.intellij.tasks.impl;

import java.awt.Dimension;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskUiUtil.ComboBoxUpdater;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

/**
 * @author Mikhail Golubev
 */
public abstract class TaskStateCombo extends JPanel
{

	private final JBLabel myHintLabel;

	public static boolean stateUpdatesSupportedFor(@Nullable Task task)
	{
		if(task == null || !task.isIssue())
		{
			return false;
		}
		final TaskRepository repository = task.getRepository();
		return repository != null && repository.isSupported(TaskRepository.STATE_UPDATING);
	}

	private final Project myProject;
	private final Task myTask;
	private final TemplateKindCombo myKindCombo = new TemplateKindCombo();

	// For designer only
	@SuppressWarnings("unused")
	public TaskStateCombo()
	{
		this(null, null);
	}

	@SuppressWarnings({
			"GtkPreferredJComboBoxRenderer",
			"unchecked"
	})
	public TaskStateCombo(Project project, Task task)
	{
		myProject = project;
		myTask = task;

		myHintLabel = new JBLabel();
		myHintLabel.setIcon(PlatformIcons.UP_DOWN_ARROWS);
		myHintLabel.setToolTipText("Pressing Up or Down arrows while in editor changes the state");
		final JComboBox comboBox = myKindCombo.getComboBox();
		comboBox.setPreferredSize(new Dimension(300, UIUtil.fixComboBoxHeight(comboBox.getPreferredSize().height)));
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		add(myKindCombo);
		add(myHintLabel);
	}

	/**
	 * One-shot method. Update combo box items only once.
	 *
	 * @return whether update was actually scheduled
	 */
	public boolean scheduleUpdateOnce()
	{
		if(myProject != null && stateUpdatesSupportedFor(myTask) && myKindCombo.getComboBox().getItemCount() == 0)
		{
			final JComboBox comboBox = myKindCombo.getComboBox();
			final TaskRepository repository = myTask.getRepository();
			assert repository != null;
			new ComboBoxUpdater<CustomStateTrinityAdapter>(myProject, "Fetching available task states...", comboBox)
			{
				@Nonnull
				@Override
				protected List<CustomStateTrinityAdapter> fetch(@Nonnull ProgressIndicator indicator) throws Exception
				{
					return CustomStateTrinityAdapter.wrapList(repository.getAvailableTaskStates(myTask));
				}

				@Nullable
				@Override
				public CustomStateTrinityAdapter getSelectedItem()
				{
					final CustomTaskState state = getPreferredState(repository, CustomStateTrinityAdapter.unwrapList(myResult));
					return state != null ? new CustomStateTrinityAdapter(state) : null;
				}
			}.queue();
			return true;
		}
		return false;
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		myKindCombo.setEnabled(enabled);
	}

	public void showHintLabel(boolean show)
	{
		myHintLabel.setVisible(show);
	}

	/**
	 * @return {@code null} if no state is available at the moment or special "do not update" state was selected
	 */
	@Nullable
	public CustomTaskState getSelectedState()
	{
		final CustomStateTrinityAdapter item = (CustomStateTrinityAdapter) myKindCombo.getComboBox().getSelectedItem();
		if(item == null)
		{
			return null;
		}
		return item.myState;
	}

	public void registerUpDownAction(@Nonnull JComponent focusable)
	{
		myKindCombo.registerUpDownHint(focusable);
	}

	@Nonnull
	public JComboBox getComboBox()
	{
		return myKindCombo.getComboBox();
	}

	/**
	 * Determine what state should be initially selected in the list.
	 *
	 * @param repository task repository to communicate with
	 * @param available  tasks states already downloaded from the repository
	 * @return task state to select
	 */
	@Nullable
	protected abstract CustomTaskState getPreferredState(@Nonnull TaskRepository repository, @Nonnull Collection<CustomTaskState> available);

	private static class CustomStateTrinityAdapter extends Trinity<String, Icon, String>
	{
		final CustomTaskState myState;

		public CustomStateTrinityAdapter(@Nonnull CustomTaskState state)
		{
			super(state.getPresentableName(), null, state.getId());
			myState = state;
		}

		@Nonnull
		static List<CustomStateTrinityAdapter> wrapList(@Nonnull Collection<CustomTaskState> states)
		{
			return ContainerUtil.map(states, state -> new CustomStateTrinityAdapter(state));
		}

		@Nonnull
		static List<CustomTaskState> unwrapList(@Nonnull Collection<CustomStateTrinityAdapter> wrapped)
		{
			return ContainerUtil.map(wrapped, adapter -> adapter.myState);
		}
	}
}
