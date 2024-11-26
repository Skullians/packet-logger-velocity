import sqlite3
import plotly.graph_objects as go


def get_packet_totals_per_minute(db_file, outgoing_filter=None):
    """
    Fetch packet totals per minute for each packet name.

    Args:
        db_file (str): Path to the SQLite file.
        outgoing_filter (str): Optional filter ('outgoing', 'incoming', or None for all packets).

    Returns:
        dict: A dictionary where keys are timestamps (in minutes), and values are dictionaries
              of packet names and their totals for that minute.
    """
    with sqlite3.connect(db_file) as conn:
        cursor = conn.cursor()

        # Base query to get packet totals
        query = """
            SELECT
                packet_name,
                SUM(amount) AS total_amount,
                strftime('%Y-%m-%d %H:%M', datetime(collected_at / 1000, 'unixepoch')) AS minute
            FROM batched_packets
        """

        # Apply filter based on outgoing field if specified
        if outgoing_filter == "outgoing":
            query += " WHERE outgoing = 1 "
        elif outgoing_filter == "incoming":
            query += " WHERE outgoing = 0 "

        query += "GROUP BY packet_name, minute ORDER BY minute"

        cursor.execute(query)
        rows = cursor.fetchall()

    # Organize the data into a dictionary: {minute: {packet_name: total_amount}}
    packet_totals = {}
    for row in rows:
        packet_name = row[0]
        total_amount = row[1]
        minute = row[2]

        if minute not in packet_totals:
            packet_totals[minute] = {}

        packet_totals[minute][packet_name] = total_amount

    return packet_totals


def plot_packet_totals(packet_totals):
    """
    Plot the packet totals for each packet name over time (minute by minute).

    Args:
        packet_totals (dict): A dictionary where keys are minutes and values are packet totals.
    """
    # Extract all packet names
    packet_names = set()
    for minute_data in packet_totals.values():
        packet_names.update(minute_data.keys())

    # Convert minutes (timestamps) into a sorted list of time slots
    times = sorted(packet_totals.keys())
    packet_name_totals = {name: [] for name in packet_names}

    # Fill packet_totals for each minute and packet name
    for time in times:
        for packet_name in packet_names:
            packet_name_totals[packet_name].append(
                packet_totals[time].get(packet_name, 0))

    # Prepare data for plotly (stacked bars)
    fig = go.Figure()

    # Stacked bars for each packet name
    for packet_name in packet_names:
        fig.add_trace(go.Bar(
            x=times,
            y=packet_name_totals[packet_name],
            name=packet_name,
            hovertemplate='<b>Packet Name:</b> ' + packet_name + '<br>' +
                          '<b>Time:</b> %{x}<br>' +
                          # Customize hover template
                          '<b>Total Packets:</b> %{y}<extra></extra>',
            # White border around bars
            marker=dict(line=dict(color='rgba(255,255,255,0.3)'))
        ))

    # Update layout to include a scrollable legend
    fig.update_layout(
        title="Packet Totals Sent Per Minute",
        xaxis_title="Time (Minutes)",
        yaxis_title="Total Packets Sent",
        barmode="stack",  # Stack bars
        xaxis=dict(tickmode="array", tickvals=times, ticktext=times),
        showlegend=True,  # Enable the legend
        legend=dict(
            x=1,  # Position legend to the right of the plot
            y=1,
            traceorder='normal',
            orientation='v',  # Vertical legend
            font=dict(size=10),  # Font size for legend
            bgcolor='rgba(255, 255, 255, 0.7)',  # Background color for legend
            # Border color for the legend
            bordercolor='rgba(255, 255, 255, 0.5)',
            borderwidth=1,
            itemclick="toggleothers",  # Clicking an item hides other packets
            itemsizing='constant',  # Ensure the legend items are the same size
            xanchor='left',  # Anchor position
            yanchor='top',  # Anchor position
            tracegroupgap=3,  # Space between items
        ),
        plot_bgcolor="white",  # Set background to white
    )

    # Show the plot
    fig.show()


if __name__ == "__main__":
    db_file = "packet.db"

    # User input for filtering packets: "all", "outgoing", or "incoming"
    outgoing_filter = input(
        "Enter packet type filter ('all', 'outgoing', 'incoming'): ").strip().lower()

    if outgoing_filter not in ["all", "outgoing", "incoming"]:
        print("Invalid input. Please enter 'all', 'outgoing', or 'incoming'.")
    else:
        # Get the packet totals per minute based on the user filter
        packet_totals = get_packet_totals_per_minute(db_file, outgoing_filter)

        # Plot the result
        plot_packet_totals(packet_totals)
