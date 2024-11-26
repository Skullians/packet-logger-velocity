import sqlite3
import plotly.graph_objects as go


def get_packet_totals(db_file, time_granularity="minute", outgoing_filter=None):
    """
    Fetch packet totals per time unit (minute or hour) for each packet name.
    Args:
        db_file (str): Path to the SQLite file.
        time_granularity (str): Either 'minute' or 'hour' for the time grouping.
        outgoing_filter (str): Optional filter ('outgoing', 'incoming', or None for all packets).
    Returns:
        dict: A dictionary where keys are timestamps (minute or hour), and values are dictionaries
              of packet names and their totals for that time period.
    """
    with sqlite3.connect(db_file) as conn:
        cursor = conn.cursor()
        # Adjust the query to use 'strftime' for hour or minute depending on the time_granularity
        time_format = '%Y-%m-%d %H:%M' if time_granularity == "minute" else '%Y-%m-%d %H'
        query = f"""
            SELECT
                batched_packets.packet_name,
                SUM(batched_packets.amount) AS total_amount,
                strftime('{time_format}', datetime(batched_packets.collected_at / 1000, 'unixepoch')) AS time_period
            FROM batched_packets
            JOIN packet_bound ON batched_packets.packet_name = packet_bound.packet_name
        """
        # Apply filter based on outgoing field from the packet_bound table if specified
        if outgoing_filter == "outgoing":
            query += " WHERE packet_bound.outgoing = 1 "
        elif outgoing_filter == "incoming":
            query += " WHERE packet_bound.outgoing = 0 "

        query += f" GROUP BY batched_packets.packet_name, time_period ORDER BY time_period"
        cursor.execute(query)
        rows = cursor.fetchall()

    # Organize the data into a dictionary: {time_period: {packet_name: total_amount}}
    packet_totals = {}
    for row in rows:
        packet_name = row[0]
        total_amount = row[1]
        time_period = row[2]
        if time_period not in packet_totals:
            packet_totals[time_period] = {}
        packet_totals[time_period][packet_name] = total_amount

    return packet_totals


def plot_packet_totals(packet_totals, time_granularity="minute"):
    """
    Plot the packet totals for each packet name over time (minute or hour).
    Args:
        packet_totals (dict): A dictionary where keys are time periods and values are packet totals.
        time_granularity (str): Either 'minute' or 'hour' for the time granularity of the x-axis.
    """
    # Extract all packet names
    packet_names = set()
    for time_data in packet_totals.values():
        packet_names.update(time_data.keys())

    # Convert time periods (timestamps) into a sorted list of time slots
    times = sorted(packet_totals.keys())
    packet_name_totals = {name: [] for name in packet_names}

    # Fill packet_totals for each time period and packet name
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
                          '<b>Total Packets:</b> %{y}<extra></extra>',
            # White border around bars
            marker=dict(line=dict(color='rgba(255,255,255,0.3)'))
        ))

    # Update layout to include a scrollable legend and dynamic tick format
    fig.update_layout(
        title=f"Packet Totals Sent Per {time_granularity.capitalize()}",
        xaxis_title=f"Time ({time_granularity.capitalize()})",
        yaxis_title="Total Packets Sent",
        barmode="stack",  # Stack bars
        xaxis=dict(
            tickmode="array",
            tickvals=times,
            ticktext=times,
            tickformat='%Y-%m-%d %H:%M' if time_granularity == 'minute' else '%Y-%m-%d %H'
        ),
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
    db_file = "packet.sqlite"
    # User input for filtering packets: "all", "outgoing", or "incoming"
    outgoing_filter = input(
        "Enter packet type filter ('all', 'outgoing', 'incoming'): ").strip().lower()

    # User input for time granularity
    time_granularity = input(
        "Enter time granularity ('minute' or 'hour'): ").strip().lower()

    if outgoing_filter not in ["all", "outgoing", "incoming"]:
        print("Invalid input. Please enter 'all', 'outgoing', or 'incoming'.")
    elif time_granularity not in ["minute", "hour"]:
        print("Invalid input. Please enter 'minute' or 'hour'.")
    else:
        # Get the packet totals based on user input
        packet_totals = get_packet_totals(
            db_file,
            time_granularity=time_granularity,
            outgoing_filter="all" if outgoing_filter == "all" else outgoing_filter
        )

        # Plot the result
        plot_packet_totals(packet_totals, time_granularity=time_granularity)